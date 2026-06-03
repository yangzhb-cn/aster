package com.aster.app.tool.todo;

import com.aster.app.todo.TodoStore;
import com.aster.app.todo.model.TodoItem;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * todo 工具。
 *
 * <p>它让 Agent 读写 Web 右栏的同一份便签待办清单。
 * 工具只管理清单数据，不负责后台扫描和通知。</p>
 */
public class TodoTool {
    private final ObjectMapper objectMapper;
    private final TodoStore todoStore;

    public TodoTool(ObjectMapper objectMapper, TodoStore todoStore) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.todoStore = Objects.requireNonNull(todoStore);
    }

    /**
     * 返回模型可见的工具定义。
     */
    public Tool definition() {
        return Tool.local(
                "todo",
                "Todo",
                """
                        管理 Web 右栏便签待办清单。Web 用户和 Agent 共用同一份清单。

                        支持 action：
                        - list：列出当前待办
                        - add：新增待办，content 必填，dueAt 可选
                        - update：更新待办，id 必填
                        - complete：标记完成，id 必填
                        - archive：归档隐藏，id 必填

                        dueAt 必须使用 ISO-8601 Instant，例如 2026-06-04T10:30:00Z。
                        没有 dueAt 的待办不会被后台扫描器自动提醒。
                        """.strip(),
                objectSchema(Map.of(
                        "action", stringSchema("操作类型：list/add/update/complete/archive"),
                        "id", stringSchema("待办 id，update/complete/archive 必填"),
                        "content", stringSchema("待办内容，add 必填，update 可选"),
                        "priority", stringSchema("优先级：high/medium/low，可选"),
                        "dueAt", stringSchema("到期时间，ISO-8601 Instant 字符串，可选"),
                        "result", stringSchema("完成说明，complete 可选")
                ), List.of("action"))
        );
    }

    /**
     * 执行 todo 管理操作。
     */
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        String action = requiredString(arguments, "action");
        return switch (action) {
            case "list" -> ToolResult.text(call.id(), render(todoStore.listActive()));
            case "add" -> ToolResult.text(call.id(), render(todoStore.add(
                    requiredString(arguments, "content"),
                    optionalString(arguments, "priority"),
                    optionalString(arguments, "dueAt")
            )));
            case "update" -> ToolResult.text(call.id(), render(todoStore.update(
                    requiredString(arguments, "id"),
                    optionalString(arguments, "content"),
                    optionalString(arguments, "priority"),
                    optionalString(arguments, "dueAt")
            )));
            case "complete" -> ToolResult.text(call.id(), render(todoStore.complete(
                    requiredString(arguments, "id"),
                    optionalString(arguments, "result")
            )));
            case "archive" -> ToolResult.text(call.id(), render(todoStore.archive(requiredString(arguments, "id"))));
            default -> ToolResult.error(call.id(), "未知 todo action: " + action);
        };
    }

    /**
     * 注册到工具表。
     */
    public void registerTo(ToolRegistry toolRegistry) {
        toolRegistry.registerLocal(definition(), this::execute);
    }

    private String render(Object value) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
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

    private static String requiredString(Map<String, Object> arguments, String name) {
        String value = optionalString(arguments, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("缺少必填字符串参数: " + name);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
