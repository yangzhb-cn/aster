package com.aster.core.tool;

import com.aster.llm.model.ToolCall;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.core.tool.model.ToolSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型可见工具的统一注册表。
 *
 * <p>AgentLoop 只和这个类交互。工具到底是本地 Java 代码，
 * 还是 MCP 远端工具，都在这里通过 ToolSource 路由隐藏起来。</p>
 */
public class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final LocalToolExecutor localExecutor;
    private final ToolExecutor mcpExecutor;

    public ToolRegistry(LocalToolExecutor localExecutor, ToolExecutor mcpExecutor) {
        this.localExecutor = localExecutor;
        this.mcpExecutor = mcpExecutor;
    }

    /**
     * 注册工具定义。
     *
     * <p>MCP 工具加载后已经带着远端执行信息，所以只需要登记 Tool。
     * 本地工具需要用 registerLocal，同时登记 Tool 和 Java 处理函数。</p>
     */
    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * 统一注册本地工具。
     *
     * <p>本地工具必须同时进入两个地方：ToolRegistry 负责把工具暴露给 LLM，
     * LocalToolExecutor 负责真正执行 Java 处理函数。这个方法把两步收在一起，
     * 避免调用处漏注册其中一边。</p>
     */
    public void registerLocal(Tool tool, ToolHandler handler) {
        localExecutor.register(tool.name(), handler);
        register(tool);
    }

    /**
     * 按注册顺序列出工具。
     */
    public List<Tool> listTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 执行模型请求的一次工具调用。
     */
    public ToolResult execute(ToolCall call) {
        Tool tool = tools.get(call.function().name());
        if (tool == null) {
            // 即使未知工具，也返回 ToolResult，而不是直接抛异常。
            // 原因是 assistant 已经产生了 tool_call，Agent 必须写回一个 role=tool
            // 来完成协议配对，再让模型自己处理“工具不存在”的结果。
            return ToolResult.error(call.id(), "Unknown tool: " + call.function().name());
        }

        if (tool.source() == ToolSource.MCP) {
            // MCP 工具不侵入 AgentLoop。这里只按 ToolSource 路由到远程执行器。
            return mcpExecutor.execute(tool, call);
        }
        // 本地工具和 MCP 工具返回同一种 ToolResult，后续统一转成 role=tool 消息。
        return localExecutor.execute(tool, call);
    }

    public List<Map<String, Object>> toLlmToolSchemas() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Tool tool : tools.values()) {
            // OpenAI-compatible 工具格式：type=function + function(name, description, parameters)。
            // MCP tools/list 里的 inputSchema 已经是 JSON Schema，适配成 Tool 后可直接复用。
            result.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", tool.inputSchema()
                    )
            ));
        }

        return result;
    }
}
