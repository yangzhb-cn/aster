package com.aster.app.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolContent;
import com.aster.core.tool.model.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 协议对象和本项目 Tool 模型之间的转换器。
 *
 * <p>这里是边界层：MCP 返回 JSON-RPC 风格的数据，AgentLoop 只认识统一 Tool/ToolResult。
 * 适配器把差异收在这里，避免上层到处判断 MCP 细节。</p>
 */
public final class McpToolAdapter {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private McpToolAdapter() {
    }

    /**
     * 把 MCP 的工具描述转换成 OpenAI 工具结构可用的 Tool。
     */
    public static Tool toTool(String serverId, JsonNode mcpTool, ObjectMapper objectMapper) {
        // MCP 的 inputSchema 本身就是 JSON Schema。
        // 项目的 Tool 也使用 JSON Schema，所以这里不重新建模，直接保留 Map。
        Map<String, Object> inputSchema = objectMapper.convertValue(mcpTool.path("inputSchema"), MAP_TYPE);

        return Tool.mcp(
                serverId,
                mcpTool.path("name").asText(),
                textOrDefault(mcpTool.path("title"), mcpTool.path("name").asText()),
                textOrDefault(mcpTool.path("description"), ""),
                inputSchema
        );
    }

    /**
     * 把 MCP tool/call 的结果转换成本项目统一的 ToolResult。
     */
    public static ToolResult toToolResult(String toolCallId, JsonNode mcpResult) {
        // MCP 工具结果是内容块；LLM 聊天 API 需要的是一条 role=tool 文本。
        // 这里先保留统一 ToolResult，最后由 AgentLoop 渲染成 Message.tool。
        boolean isError = mcpResult.path("isError").asBoolean(false);
        List<ToolContent> contents = new ArrayList<>();

        for (JsonNode block : mcpResult.path("content")) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                contents.add(ToolContent.text(block.path("text").asText()));
            } else {
                // MVP 暂时不完整建模所有媒体类型，但会把非文本 block 显示出来。
                contents.add(ToolContent.text("[Unsupported MCP content type: " + type + "]"));
            }
        }

        if (contents.isEmpty()) {
            contents.add(ToolContent.text(""));
        }

        return new ToolResult(toolCallId, isError, contents);
    }

    /**
     * MCP 字段可能缺失，用默认值能让工具列表加载更稳。
     */
    private static String textOrDefault(JsonNode node, String defaultValue) {
        return node == null || node.isMissingNode() || node.isNull() ? defaultValue : node.asText();
    }
}
