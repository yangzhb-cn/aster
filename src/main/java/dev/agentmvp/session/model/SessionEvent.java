package dev.agentmvp.session.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import dev.agentmvp.llm.model.Message;

/**
 * JSONL session 的单行事件。
 *
 * <p>每一行都是一个不可变事实：创建 session、创建分支、追加消息、run 开始或结束。
 * prevHash/hash 用于形成简单哈希链，方便后续审计时发现中间行被改动。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({
        "seq",
        "eventId",
        "sessionId",
        "branchId",
        "type",
        "createdAt",
        "runId",
        "parentBranchId",
        "forkSeq",
        "message",
        "text",
        "prevHash",
        "hash"
})
public record SessionEvent(
        long seq,
        String eventId,
        String sessionId,
        String branchId,
        String type,
        String createdAt,
        String runId,
        String parentBranchId,
        Long forkSeq,
        Message message,
        String text,
        String prevHash,
        String hash
) {
    /**
     * 创建一条还没写入审计字段的事件。
     */
    public static SessionEvent draft(
            String sessionId,
            String branchId,
            SessionEventType type,
            String runId,
            String parentBranchId,
            Long forkSeq,
            Message message,
            String text
    ) {
        return new SessionEvent(
                0,
                null,
                sessionId,
                branchId,
                type.value(),
                null,
                runId,
                parentBranchId,
                forkSeq,
                message,
                text,
                null,
                null
        );
    }

    /**
     * 写入 seq、eventId、createdAt、prevHash、hash 前，先得到待计算哈希的事件形状。
     */
    public SessionEvent withAuditBase(long seq, String eventId, String createdAt, String prevHash) {
        return new SessionEvent(
                seq,
                eventId,
                sessionId,
                branchId,
                type,
                createdAt,
                runId,
                parentBranchId,
                forkSeq,
                message,
                text,
                prevHash,
                null
        );
    }

    /**
     * 补上最终 hash。
     */
    public SessionEvent withHash(String hash) {
        return new SessionEvent(
                seq,
                eventId,
                sessionId,
                branchId,
                type,
                createdAt,
                runId,
                parentBranchId,
                forkSeq,
                message,
                text,
                prevHash,
                hash
        );
    }
}
