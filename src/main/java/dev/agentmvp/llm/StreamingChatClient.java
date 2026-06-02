package dev.agentmvp.llm;

import dev.agentmvp.llm.model.ChatRequest;
import dev.agentmvp.llm.model.ChatStreamChunk;

import java.io.IOException;

/**
 * 流式 LLM 客户端抽象。
 *
 * <p>项目里只保留流式路径。OpenAI 兼容的聊天流式接口会返回多片 SSE 片段，
 * 调用方负责接收这些片段，并自己组装最终 assistant 消息。</p>
 */
public interface StreamingChatClient {
    /**
     * 发送一次聊天请求，并把每片 SSE 片段推给处理器。
     */
    void stream(ChatRequest request, StreamHandler handler) throws IOException;

    interface StreamHandler {
        /**
         * 处理一条 {@code data: {...}} SSE 行解析出来的片段。
         */
        void onChunk(ChatStreamChunk chunk);

        /**
         * 当供应商发送 {@code data: [DONE]} 时调用。
         */
        default void onDone() {
        }
    }
}
