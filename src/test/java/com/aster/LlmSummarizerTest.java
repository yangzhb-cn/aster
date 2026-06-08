package com.aster;

import com.aster.core.context.LlmSummarizer;
import com.aster.core.context.Summarizer;
import com.aster.llm.text.StreamingChatClient;
import com.aster.llm.text.model.ChatRequest;
import com.aster.llm.text.model.Message;
import com.aster.llm.text.model.ProviderStreamEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 上下文摘要器测试。
 */
class LlmSummarizerTest {
    /**
     * 验证摘要器会用无工具流式请求收集模型正文。
     */
    @Test
    void streamsSummaryTextWithoutTools() {
        FakeStreamingChatClient client = new FakeStreamingChatClient("上下文摘要");
        LlmSummarizer summarizer = new LlmSummarizer(
                "deepseek-test",
                client,
                "请总结上下文",
                1_000,
                ignored -> "fallback"
        );

        String summary = summarizer.summarize(List.of(
                Message.user("用户请求"),
                Message.assistant("助手回答")
        ));

        assertEquals("上下文摘要", summary);
        assertEquals("deepseek-test", client.request.model());
        assertTrue(client.request.tools().isEmpty());
        assertNull(client.request.toolChoice());
        assertNull(client.request.thinking());
        assertTrue(client.request.messages().get(0).content().contains("请总结上下文"));
        assertTrue(client.request.messages().get(1).content().contains("user: 用户请求"));
    }

    /**
     * 验证 LLM 摘要失败时回退到确定性摘要器。
     */
    @Test
    void fallsBackWhenLlmFails() {
        Summarizer fallback = ignored -> "fallback summary";
        LlmSummarizer summarizer = new LlmSummarizer(
                "deepseek-test",
                new FailingStreamingChatClient(),
                "请总结上下文",
                1_000,
                fallback
        );

        assertEquals("fallback summary", summarizer.summarize(List.of(Message.user("用户请求"))));
    }

    private static class FakeStreamingChatClient implements StreamingChatClient {
        private final String text;
        private ChatRequest request;

        private FakeStreamingChatClient(String text) {
            this.text = text;
        }

        @Override
        public void stream(ChatRequest request, StreamHandler handler) {
            this.request = request;
            handler.onEvent(new ProviderStreamEvent.TextDelta(text));
            handler.onDone();
        }
    }

    private static class FailingStreamingChatClient implements StreamingChatClient {
        @Override
        public void stream(ChatRequest request, StreamHandler handler) throws IOException {
            throw new IOException("llm unavailable");
        }
    }
}
