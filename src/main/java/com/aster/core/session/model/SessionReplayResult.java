package com.aster.core.session.model;

import com.aster.llm.model.Message;

import java.util.List;

/**
 * session 回放结果。
 *
 * <p>除了消息列表，还保留是否发生恢复裁剪。
 * 如果 JSONL 尾部有半截 tool_call，replay 会回退到最后一个合法协议点。</p>
 */
public record SessionReplayResult(
        List<Message> messages,
        List<SessionMessageRecord> messageRecords,
        long lastSeq,
        boolean recovered,
        String recoveryReason
) {
    public SessionReplayResult {
        messages = List.copyOf(messages);
        messageRecords = List.copyOf(messageRecords);
    }
}
