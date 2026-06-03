package com.aster.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.model.ToolCall;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 执行由本地 Java 处理器实现的工具。
 */
public class LocalToolExecutor implements ToolExecutor {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Map<String, ToolHandler> handlers = new HashMap<>();

    public LocalToolExecutor(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public void register(String name, ToolHandler handler) {
        handlers.put(name, handler);
    }

    /**
     * 解析模型返回的 JSON 参数字符串，再交给已注册的处理器。
     */
    @Override
    public ToolResult execute(Tool tool, ToolCall call) {
        ToolHandler handler = handlers.get(tool.name());
        if (handler == null) {
            return ToolResult.error(call.id(), "Unknown local tool: " + tool.name());
        }

        try {
            // LLM 返回的 function.arguments 是 JSON 字符串，不是已经解析好的对象。
            // ToolExecutor 这一层负责把字符串转成 Map，再交给具体处理器。
            Map<String, Object> arguments = objectMapper.readValue(call.function().argumentsJson(), MAP_TYPE);
            return handler.handle(call, arguments);
        } catch (Exception e) {
            // 未知工具或工具执行失败时，也要返回一条 tool 消息，保证协议配对不断。
            return ToolResult.error(call.id(), e.getMessage());
        }
    }
}
