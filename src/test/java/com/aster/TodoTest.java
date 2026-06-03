package com.aster;

import com.aster.app.todo.JsonTodoStore;
import com.aster.app.todo.TodoScanTaskHandler;
import com.aster.app.todo.model.TodoItem;
import com.aster.app.todo.model.TodoStatus;
import com.aster.app.tool.todo.TodoTool;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 便签待办 MVP 测试。
 */
class TodoTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * 验证 JSON store 能新增待办并按 dueAt 找到到期项。
     */
    @Test
    void storesAndFindsDueTodos() throws Exception {
        JsonTodoStore store = store();
        TodoItem item = store.add("提醒我看日志", "high", "2020-01-01T00:00:00Z");

        assertEquals(1, store.listActive().size());
        assertEquals(item.id(), store.dueItems(Instant.parse("2020-01-01T00:00:10Z")).getFirst().id());
    }

    /**
     * 验证 todo 工具和 Web 使用同一个 store 语义。
     */
    @Test
    void todoToolAddsAndListsTodos() throws Exception {
        JsonTodoStore store = store();
        ToolRegistry registry = new ToolRegistry(new LocalToolExecutor(objectMapper), null);
        new TodoTool(objectMapper, store).registerTo(registry);

        ToolResult add = registry.execute(ToolCall.function(
                "call_todo_add",
                "todo",
                objectMapper.writeValueAsString(Map.of(
                        "action", "add",
                        "content", "整理右栏便签",
                        "priority", "medium"
                ))
        ));
        ToolResult list = registry.execute(ToolCall.function(
                "call_todo_list",
                "todo",
                objectMapper.writeValueAsString(Map.of("action", "list"))
        ));

        assertTrue(add.renderText().contains("整理右栏便签"));
        assertTrue(list.renderText().contains("整理右栏便签"));
    }

    /**
     * 验证后台扫描 handler 会完成到期待办并返回通知文案。
     */
    @Test
    void scannerCompletesDueTodos() throws Exception {
        JsonTodoStore store = store();
        TodoItem item = store.add("到点推送消息", "medium", "2020-01-01T00:00:00Z");
        TodoScanTaskHandler handler = new TodoScanTaskHandler(store);

        String message = handler.handle(null);

        assertTrue(message.contains("到点推送消息"));
        assertEquals(TodoStatus.COMPLETED, store.find(item.id()).orElseThrow().status());
    }

    private JsonTodoStore store() throws Exception {
        return new JsonTodoStore(objectMapper, tempDir.resolve("todos.json"));
    }
}
