package com.aster.core.session.model;

import com.aster.llm.model.Message;

/**
 * JSONL 中一条可见 message_appended 事件的回放结果。
 *
 * <p>上下文窗口快照需要知道自己处理到了哪条原始消息。
 * 这里保留 message 本身以及对应 JSONL 事件的 seq/hash，
 * 方便恢复时校验快照没有和 session 历史错位。</p>
 */
public record SessionMessageRecord(
        long seq,
        String hash,
        Message message
) {
}
