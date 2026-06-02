package dev.agentmvp.tool.model;

/**
 * 工具来源。
 *
 * <p>LOCAL 表示本地 Java 处理器，MCP 表示来自外部 MCP 服务端。
 * AgentLoop 根据来源选择不同执行器。</p>
 */
public enum ToolSource {
    LOCAL,
    MCP
}
