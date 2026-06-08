package com.aster;

import com.aster.app.multimodal.MultimodalChatService;
import com.aster.llm.multimodal.MultimodalChatClient;
import com.aster.llm.multimodal.model.MultimodalChatRequest;
import com.aster.llm.multimodal.ollama.OllamaMultimodalProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web 图片理解服务测试。
 */
class MultimodalChatServiceTest {
    /**
     * 验证中文问题会在多模态请求中补充中文回答约束。
     */
    @Test
    void reinforcesChineseLanguageForChineseQuestion() throws Exception {
        AtomicReference<MultimodalChatRequest> captured = new AtomicReference<>();
        MultimodalChatClient client = (request, handler) -> {
            captured.set(request);
            handler.onToken("好的");
            handler.onDone();
        };
        MultimodalChatService service = new MultimodalChatService(client, new OllamaMultimodalProvider());

        StringBuilder answer = new StringBuilder();
        service.stream(
                "这是谁，中文回答",
                List.of(new MultimodalChatService.ImageAttachment("avatar.jpg", "image/jpeg", "AAA")),
                "",
                new MultimodalChatService.MultimodalStreamHandler() {
                    @Override
                    public void onStarted(MultimodalChatService.MultimodalStreamStart start) {
                    }

                    @Override
                    public void onToken(String token) {
                        answer.append(token);
                    }

                    @Override
                    public void onDone(MultimodalChatService.MultimodalAnswer result) {
                    }
                }
        );

        MultimodalChatRequest request = captured.get();
        assertEquals("system", request.messages().get(0).role());
        assertTrue(request.messages().get(0).content().getFirst().text().contains("必须使用和用户问题一致的语言"));
        assertEquals("user", request.messages().get(1).role());
        assertTrue(request.messages().get(1).content().getFirst().text().contains("请严格使用简体中文回答"));
        assertTrue(request.messages().get(1).content().getFirst().text().contains("这是谁，中文回答"));
        assertEquals("好的", answer.toString());
    }
}
