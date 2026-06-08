package com.aster.llm.text;

import com.aster.llm.text.model.ChatRequest;
import com.aster.llm.text.model.ProviderStreamEvent;

import java.io.IOException;

/**
 * 流式 LLM 客户端抽象。
 *
 * <p>项目里只保留流式路径。不同供应商的 SSE 片段会先被各自 Parser
 * 转换成 ProviderStreamEvent，调用方不用直接理解供应商原始 JSON。</p>
 */
public interface StreamingChatClient {
    /**
     * 发送一次聊天请求，并把统一流式事件推给处理器。
     */
    void stream(ChatRequest request, StreamHandler handler) throws IOException;

    interface StreamHandler {
        /**
         * 处理一条统一供应商事件。
         */
        void onEvent(ProviderStreamEvent event);

        /**
         * 当供应商发送 {@code data: [DONE]} 时调用。
         */
        default void onDone() {
            onEvent(new ProviderStreamEvent.Done());
        }
    }
}
