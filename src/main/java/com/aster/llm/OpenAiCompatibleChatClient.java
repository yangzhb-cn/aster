package com.aster.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.model.OpenAiCompatibleProvider;
import okhttp3.OkHttpClient;

/**
 * OpenAI 兼容流式 chat 客户端工厂。
 */
public final class OpenAiCompatibleChatClient {
    private OpenAiCompatibleChatClient() {
    }

    public static OkHttpStreamingChatClient create(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            OpenAiCompatibleProvider provider
    ) {
        return new OkHttpStreamingChatClient(
                httpClient,
                objectMapper,
                provider.chatCompletionsEndpoint(),
                provider.apiKey()
        );
    }
}
