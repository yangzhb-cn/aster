package com.aster.app.tool.schedule;

import com.aster.app.schedule.ScheduledUserMessageManager;
import com.aster.app.schedule.model.ScheduledUserMessage;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * schedule 工具。
 *
 * <p>它只负责管理“到点自动向当前 session 发送 user 消息”的自动化任务。
 * 后台系统维护和简单延时提醒仍由 background_task 负责。</p>
 */
public class ScheduleTool {
    private static final int MAX_LIST_SCHEDULES = 100;
    private final ScheduledUserMessageManager manager;

    public ScheduleTool(ScheduledUserMessageManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    /**
     * 返回模型可见的工具定义。
     */
    public Tool definition() {
        return Tool.local(
                "schedule",
                "Schedule User Message",
                """
                        管理自动化用户消息。适合“每天 12 点帮我总结新闻”“每周生成报告”“每小时检查网站并总结”等需要 Agent 到点自动执行的任务。

                        到点后，本工具会向当前 session 自动提交一条 user 消息，后续照常走 AgentLoop、工具调用、HITL 审批、Session 和事件流。
                        如果只是“5 分钟后提醒我一句话”，优先使用 background_task 的 reminder，不要用 schedule。

                        支持的 action：
                        - create_once：在 runAt 指定时间执行一次
                        - create_delay：delaySeconds 秒后执行一次
                        - create_interval：每 intervalSeconds 秒重复执行
                        - create_daily：每天 dailyTime 时间执行，timezone 可选
                        - list：列出当前 session 的自动化用户消息
                        - cancel：取消 schedule

                        时间规则：
                        - runAt 必须是 ISO-8601 instant，例如 2026-06-05T04:00:00Z
                        - dailyTime 使用 HH:mm 或 HH:mm:ss，例如 12:00
                        - timezone 使用 IANA 时区，例如 Asia/Shanghai；不传则使用本机默认时区
                        """.strip(),
                objectSchema(
                        Map.of(
                                "action", stringSchema("操作类型：create_once/create_delay/create_interval/create_daily/list/cancel"),
                                "name", stringSchema("任务展示名，例如 每日 AI 新闻"),
                                "content", stringSchema("到点后作为 user 消息提交给 Agent 的内容"),
                                "runAt", stringSchema("create_once 使用的 ISO-8601 instant，例如 2026-06-05T04:00:00Z"),
                                "delaySeconds", numberSchema("create_delay 使用的延迟秒数，例如 300 表示 5 分钟后"),
                                "intervalSeconds", numberSchema("create_interval 使用的固定间隔秒数，必须大于 0"),
                                "dailyTime", stringSchema("create_daily 使用的每日时间，格式 HH:mm 或 HH:mm:ss，例如 12:00"),
                                "timezone", stringSchema("create_daily 使用的 IANA 时区，例如 Asia/Shanghai"),
                                "scheduleId", stringSchema("cancel 使用的 schedule id")
                        ),
                        List.of("action")
                )
        );
    }

    /**
     * 执行 schedule 管理操作。
     */
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        String action = requiredString(arguments, "action");
        return switch (action) {
            case "create_once" -> created(call, manager.createOnce(
                    optionalString(arguments, "name", "定时用户消息"),
                    requiredString(arguments, "content"),
                    requiredString(arguments, "runAt")
            ));
            case "create_delay" -> created(call, manager.createDelay(
                    optionalString(arguments, "name", "延迟用户消息"),
                    requiredString(arguments, "content"),
                    optionalLong(arguments, "delaySeconds", 0)
            ));
            case "create_interval" -> created(call, manager.createInterval(
                    optionalString(arguments, "name", "周期用户消息"),
                    requiredString(arguments, "content"),
                    optionalLong(arguments, "intervalSeconds", 0)
            ));
            case "create_daily" -> created(call, manager.createDaily(
                    optionalString(arguments, "name", "每日用户消息"),
                    requiredString(arguments, "content"),
                    requiredString(arguments, "dailyTime"),
                    optionalString(arguments, "timezone", "")
            ));
            case "list" -> ToolResult.text(call.id(), renderSchedules(manager.listActive()));
            case "cancel" -> ToolResult.text(call.id(), "schedule 已取消：\n" + renderSchedule(manager.cancel(requiredString(arguments, "scheduleId"))));
            default -> ToolResult.error(call.id(), "未知 schedule action: " + action);
        };
    }

    /**
     * 注册到当前工具表。
     */
    public void registerTo(ToolRegistry toolRegistry) {
        toolRegistry.registerLocal(definition(), this::execute);
    }

    private ToolResult created(ToolCall call, ScheduledUserMessage schedule) {
        return ToolResult.text(call.id(), "schedule 已创建：\n" + renderSchedule(schedule));
    }

    private String renderSchedules(List<ScheduledUserMessage> schedules) {
        if (schedules.isEmpty()) {
            return "暂无自动化用户消息。";
        }
        StringBuilder output = new StringBuilder();
        List<ScheduledUserMessage> visible = schedules.stream().limit(MAX_LIST_SCHEDULES).toList();
        for (ScheduledUserMessage schedule : visible) {
            output.append(renderSchedule(schedule)).append("\n\n");
        }
        if (schedules.size() > MAX_LIST_SCHEDULES) {
            output.append("... (超过 ").append(MAX_LIST_SCHEDULES).append(" 个 schedule，仅显示前 ")
                    .append(MAX_LIST_SCHEDULES).append(" 个)");
        }
        return output.toString().stripTrailing();
    }

    private String renderSchedule(ScheduledUserMessage schedule) {
        return """
                id=%s
                name=%s
                status=%s
                sessionId=%s
                trigger=%s
                nextRunAt=%s
                updatedAt=%s
                content=%s
                """.formatted(
                schedule.id(),
                schedule.name(),
                schedule.status(),
                schedule.sessionId(),
                schedule.trigger() == null ? "" : schedule.trigger().type(),
                schedule.nextRunAt(),
                schedule.updatedAt(),
                schedule.content()
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
}
