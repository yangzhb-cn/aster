package dev.agentmvp.core.session.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 一个可切换的本地 session 摘要。
 *
 * <p>TUI 的 /session list 只需要展示名称、文件大小和更新时间，
 * 不需要读取完整 JSONL 内容。</p>
 */
public record SessionSummary(
        String name,
        Path file,
        long sizeBytes,
        Instant modifiedAt
) {
}
