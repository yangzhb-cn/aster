package dev.agentmvp.core.context.model;

/**
 * 上下文构造参数。
 *
 * <p>maxContextTokens 是窗口预算，compressThreshold 是触发线，
 * keepRecentTurns 表示压缩时保留最近几个用户轮次。</p>
 */
public record ContextOptions(
        int maxContextTokens,
        double compressThreshold,
        int keepRecentTurns
) {
    /**
     * MVP 默认值：128k 窗口、90% 触发压缩、保留最近 3 个 user turn。
     */
    public static ContextOptions defaults() {
        return new ContextOptions(128_000, 0.9, 3);
    }
}
