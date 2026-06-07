package com.aster.core.context.model;

import java.util.List;

/**
 * 上下文窗口快照。
 *
 * <p>它是可覆盖缓存，不是 session 审计日志。JSONL 负责保存完整原始历史；
 * 本快照只保存运行态上下文窗口，用来在恢复会话时跳过已经完成的压缩进度。</p>
 */
public record ContextWindowSnapshot(
        int version,
        String sessionId,
        String branchId,
        long lastSeq,
        String lastHash,
        String systemPromptHash,
        String summaryPromptHash,
        String summarizer,
        String model,
        String runningSummary,
        List<Turn> recentTurns,
        String updatedAt
) {
    public static final int CURRENT_VERSION = 1;

    public ContextWindowSnapshot {
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
    }
}
