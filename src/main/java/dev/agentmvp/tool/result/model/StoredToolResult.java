package dev.agentmvp.tool.result.model;

/**
 * 外部保存的一条工具结果记录。
 *
 * <p>文件格式使用 JSONL：一行就是一个完整 JSON 对象。
 * 这样后续即使想把多个工具结果追加到同一个文件，也不需要改变数据格式。</p>
 */
public record StoredToolResult(
        String recordId,
        String storedAt,
        String toolCallId,
        String toolName,
        boolean error,
        long elapsedMillis,
        int charCount,
        String text
) {
}
