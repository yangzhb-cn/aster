package dev.agentmvp.tool.builtin.model;

import java.util.Map;

/**
 * write 工具的入参。
 *
 * <p>write 会创建或覆盖文件，因此参数保持很小：目标 path 和完整 content。</p>
 */
public record WriteToolParams(String path, String content) {
    /**
     * 从工具参数 Map 构造 write 入参。
     */
    public static WriteToolParams from(Map<String, Object> arguments) {
        return new WriteToolParams(
                ToolParamReader.requiredString(arguments, "path"),
                ToolParamReader.requiredStringAllowEmpty(arguments, "content")
        );
    }
}
