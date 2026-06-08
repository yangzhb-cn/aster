package com.aster.core.context;

import com.aster.llm.text.model.Message;

import java.util.List;

/**
 * 估算候选上下文的 token 占用。
 */
public interface TokenEstimator {
    /**
     * 返回给定消息列表的估算 token 数。
     */
    int estimate(List<Message> messages);
}
