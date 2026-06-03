package com.aster.app.mcp.server;

import com.aster.app.mcp.server.model.LocalMcpTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 MCP 服务端的工具注册表。
 *
 * <p>它把“工具描述”和“Java 处理函数”绑在一起。tools/list 只返回工具描述，
 * tools/call 才会根据 name 找到对应处理函数执行。</p>
 */
public class LocalMcpToolRegistry {
    private final Map<String, LocalMcpTool> tools = new LinkedHashMap<>();
    private final Map<String, LocalMcpToolHandler> handlers = new LinkedHashMap<>();

    /**
     * 注册一个本地 MCP 工具。
     */
    public void register(LocalMcpTool tool, LocalMcpToolHandler handler) {
        tools.put(tool.name(), tool);
        handlers.put(tool.name(), handler);
    }

    /**
     * 按注册顺序列出工具描述。
     */
    public List<LocalMcpTool> listTools() {
        return List.copyOf(tools.values());
    }

    /**
     * 按工具名查找处理函数。
     */
    public LocalMcpToolHandler handler(String name) {
        return handlers.get(name);
    }
}
