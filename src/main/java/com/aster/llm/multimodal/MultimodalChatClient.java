package com.aster.llm.multimodal;

import com.aster.llm.multimodal.model.MultimodalChatRequest;

import java.io.IOException;

/**
 * 多模态对话客户端。
 *
 * <p>它只负责图文输入和文本输出，不处理工具调用、Session 或 AgentLoop。
 * 第一版用于 Web Chat 的图片分支，后续再决定是否并入 Agent 主链路。</p>
 */
public interface MultimodalChatClient {
    /**
     * 以流式方式发送多模态请求。
     */
    void stream(MultimodalChatRequest request, StreamHandler handler) throws IOException;

    /**
     * 多模态响应流回调。
     */
    interface StreamHandler {
        /**
         * 模型输出的可见文本增量。
         */
        void onToken(String token);

        /**
         * 响应结束。
         */
        void onDone();
    }
}
