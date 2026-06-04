package com.aster.app.tool.background;

import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.TaskAction;
import com.aster.app.background.model.TaskTrigger;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * background_task 工具。
 *
 * <p>它只暴露系统后台任务和延时提醒能力。需要 Agent 到点自动执行的
 * 用户任务应使用 schedule 工具，不应塞到后台 handler 里。</p>
 */
public class BackgroundTaskTool {
    private static final int MAX_LIST_TASKS = 100;
    private final BackgroundTaskManager backgroundTaskManager;

    public BackgroundTaskTool(BackgroundTaskManager backgroundTaskManager) {
        this.backgroundTaskManager = Objects.requireNonNull(backgroundTaskManager);
    }

    /**
     * 返回模型可见的工具定义。
     */
    public Tool definition() {
        return Tool.local(
                "background_task",
                "Background Task",
                """
                        管理 Aster 后台任务和延时提醒。适合“5 分钟后提醒我一句话”、长期记忆抽取、便签扫描等不需要 Agent 自动思考的非阻塞任务。
                        如果用户要求“每天 12 点帮我总结新闻”“定期检查网站并回答”等需要 Agent 到点执行的任务，必须改用 schedule 工具。
                        不要用 bash sleep 来模拟等待。

                        支持的 action：
                        - create_immediate：创建后由后台调度器尽快执行一次
                        - create_delay：创建后等待 delaySeconds 秒再执行一次
                        - create_interval：创建后按 intervalSeconds 秒固定间隔重复执行提醒或系统维护任务
                        - list：列出任务定义
                        - cancel：取消任务

                        taskType 表示到期后真正执行的动作，必须有已注册的 BackgroundTaskHandler 支持。
                        当前可用 taskType：
                        - reminder：到期后发送提醒，params.text 是提醒内容
                        - memory_extract：提交长期记忆抽取任务，一般由系统自动使用
                        - todo_scan：系统内部便签待办扫描任务，不要手动创建

                        触发规则：
                        - create_delay 必须传 delaySeconds
                        - create_interval 必须传 intervalSeconds，必须大于 0
                        - 后台任务由后台扫描器检查任务清单，不阻塞当前对话
                        """.strip(),
                objectSchema(
                        Map.of(
                                "action", stringSchema("操作类型：create_immediate/create_delay/create_interval/list/cancel。创建延时提醒优先用 create_delay"),
                                "name", stringSchema("任务展示名，创建任务时可选，例如 10 秒后提醒"),
                                "taskType", stringSchema("任务到期后执行的动作类型。当前常用 reminder，系统任务可用 memory_extract"),
                                "params", Map.of(
                                        "type", "object",
                                        "description", "传给任务 handler 的参数对象。reminder 需要 text 字段，例如 {\"text\":\"提醒内容\"}"
                                ),
                                "delaySeconds", numberSchema("延迟秒数，仅 create_delay 使用。例如 60 表示 60 秒后执行"),
                                "intervalSeconds", numberSchema("固定间隔秒数，仅 create_interval 使用，必须大于 0。例如 3600 表示每小时执行一次"),
                                "taskId", stringSchema("任务 id，仅 cancel 使用")
                        ),
                        List.of("action")
                )
        );
    }

    /**
     * 执行后台任务管理操作。
     */
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        String action = requiredString(arguments, "action");
        return switch (action) {
            case "create_immediate" -> createTask(call, arguments, TaskTrigger.immediate());
            case "create_delay" -> createTask(call, arguments, TaskTrigger.delay(optionalLong(arguments, "delaySeconds", 0)));
            case "create_interval" -> createIntervalTask(call, arguments);
            case "list" -> ToolResult.text(call.id(), renderTasks(backgroundTaskManager.listTasks()));
            case "cancel" -> cancelTask(call, arguments);
            default -> ToolResult.error(call.id(), "未知 background_task action: " + action);
        };
    }

    /**
     * 注册到当前工具表。
     */
    public void registerTo(ToolRegistry toolRegistry) {
        toolRegistry.registerLocal(definition(), this::execute);
    }

    private ToolResult createIntervalTask(ToolCall call, Map<String, Object> arguments) throws Exception {
        long intervalSeconds = optionalLong(arguments, "intervalSeconds", 0);
        if (intervalSeconds <= 0) {
            return ToolResult.error(call.id(), "create_interval 需要 intervalSeconds > 0");
        }
        return createTask(call, arguments, TaskTrigger.interval(intervalSeconds));
    }

    private ToolResult createTask(ToolCall call, Map<String, Object> arguments, TaskTrigger trigger) throws Exception {
        String taskType = requiredString(arguments, "taskType");
        String name = optionalString(arguments, "name", taskType);
        BackgroundTask task = BackgroundTask.create(
                name,
                trigger,
                new TaskAction(taskType, objectMap(arguments.get("params")))
        );
        BackgroundTask created = backgroundTaskManager.create(task);
        return ToolResult.text(call.id(), "后台任务已创建：\n" + renderTask(created));
    }

    private ToolResult cancelTask(ToolCall call, Map<String, Object> arguments) throws Exception {
        String taskId = requiredString(arguments, "taskId");
        backgroundTaskManager.cancel(taskId);
        return ToolResult.text(call.id(), "后台任务已取消: " + taskId);
    }

    private String renderTasks(List<BackgroundTask> tasks) {
        if (tasks.isEmpty()) {
            return "暂无后台任务。";
        }
        StringBuilder output = new StringBuilder();
        List<BackgroundTask> visible = tasks.stream().limit(MAX_LIST_TASKS).toList();
        for (BackgroundTask task : visible) {
            output.append(renderTask(task)).append("\n\n");
        }
        if (tasks.size() > MAX_LIST_TASKS) {
            output.append("... (超过 ").append(MAX_LIST_TASKS).append(" 个任务，仅显示前 ")
                    .append(MAX_LIST_TASKS).append(" 个)");
        }
        return output.toString().stripTrailing();
    }

    private String renderTask(BackgroundTask task) {
        TaskTrigger trigger = task.trigger();
        TaskAction action = task.action();
        return """
                id=%s
                name=%s
                status=%s
                enabled=%s
                trigger=%s delaySeconds=%s intervalSeconds=%s
                action=%s
                updatedAt=%s
                """.formatted(
                task.id(),
                task.name(),
                task.status(),
                task.enabled(),
                trigger.type(),
                trigger.delaySeconds(),
                trigger.intervalSeconds(),
                action.type(),
                task.updatedAt()
        ).stripTrailing();
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", required
        );
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> numberSchema(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static String requiredString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("缺少必填字符串参数: " + name);
        }
        return text.trim();
    }

    private static String optionalString(Map<String, Object> arguments, String name, String defaultValue) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? defaultValue : text;
    }

    private static long optionalLong(Map<String, Object> arguments, String name, long defaultValue) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }
}
