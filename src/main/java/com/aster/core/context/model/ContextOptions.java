package com.aster.core.context.model;

/**
 * 上下文构造参数。
 *
 * <p>maxContextTokens 是窗口预算，compressThreshold 是触发线，
 * keepRecentTurns 表示压缩时在当前最后一个 user turn 之前，
 * 额外保留最近几个已完成用户轮次。</p>
 */
public record ContextOptions(
        int maxContextTokens,
        double compressThreshold,
        int keepRecentTurns
) {
    /**
     * MVP 默认值：128k 窗口、90% 触发压缩、保留当前 turn 前最近 3 个已完成 user turn。
     */
    public static ContextOptions defaults() {
        return new ContextOptions(128_000, 0.9, 3);
    }
}
