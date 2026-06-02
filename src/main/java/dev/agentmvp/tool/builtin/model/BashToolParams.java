package dev.agentmvp.tool.builtin.model;

import java.util.Map;

/**
 * bash 工具的入参。
 *
 * <p>command 是要执行的 bash 命令；timeoutSeconds 用来避免命令长时间挂住。
 * 命令会在传入的工作目录里执行，但不会限制它访问其他路径。</p>
 */
public record BashToolParams(String command, int timeoutSeconds) {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int HARD_TIMEOUT_SECONDS = 120;

    /**
     * 从工具参数 Map 构造 bash 入参。
     */
    public static BashToolParams from(Map<String, Object> arguments) {
        return new BashToolParams(
                ToolParamReader.requiredString(arguments, "command"),
                ToolParamReader.optionalInt(arguments, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS, HARD_TIMEOUT_SECONDS)
        );
    }
}
