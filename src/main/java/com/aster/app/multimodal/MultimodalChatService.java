package com.aster.app.multimodal;

import com.aster.llm.multimodal.MultimodalChatClient;
import com.aster.llm.multimodal.model.ContentPart;
import com.aster.llm.multimodal.model.MultimodalChatRequest;
import com.aster.llm.multimodal.model.MultimodalMessage;
import com.aster.llm.multimodal.ollama.OllamaMultimodalProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Web Chat 图片分支的多模态服务。
 *
 * <p>第一版只负责“图片 + 提问 -> 流式文本回答”，不写普通 Session，
 * 不触发工具调用，也不进入 AgentLoop。</p>
 */
public class MultimodalChatService {
    private static final String SYSTEM_PROMPT = """
            你是 Aster 的图片理解助手。
            必须使用和用户问题一致的语言回答；如果用户使用中文或明确要求中文，必须使用简体中文。
            回答要直接，不要切换到英文，除非用户明确要求英文。
            """;

    private final MultimodalChatClient client;
    private final OllamaMultimodalProvider provider;

    public MultimodalChatService(MultimodalChatClient client, OllamaMultimodalProvider provider) {
        this.client = Objects.requireNonNull(client);
        this.provider = Objects.requireNonNull(provider);
    }

    /**
     * 使用 Ollama 多模态模型流式回答图文问题。
     */
    public void stream(String text, List<ImageAttachment> images, String model, MultimodalStreamHandler handler) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("images is required");
        }
        String selectedModel = provider.modelOrDefault(model);
        String question = text == null || text.isBlank() ? "请描述这张图片。" : text.strip();
        List<ContentPart> parts = new ArrayList<>();
        parts.add(ContentPart.text(reinforceLanguage(question)));
        for (ImageAttachment image : images) {
            parts.add(ContentPart.imageData(image.safeMimeType(), image.contentBase64()));
        }

        handler.onStarted(new MultimodalStreamStart(selectedModel, images.size()));
        StringBuilder answer = new StringBuilder();
        client.stream(MultimodalChatRequest.streaming(
                selectedModel,
                List.of(
                        MultimodalMessage.system(SYSTEM_PROMPT),
                        MultimodalMessage.user(parts)
                )
        ), new MultimodalChatClient.StreamHandler() {
            @Override
            public void onToken(String token) {
                answer.append(token);
                handler.onToken(token);
            }

            @Override
            public void onDone() {
                handler.onDone(new MultimodalAnswer(selectedModel, answer.toString()));
            }
        });
    }

    /**
     * 默认多模态模型。
     */
    public String defaultModel() {
        return provider.defaultModel();
    }

    /**
     * 可选多模态模型列表。
     */
    public List<String> availableModels() {
        return provider.switchableModels();
    }

    /**
     * 对中文问题做额外语言约束，降低本地视觉模型默认英文回答的概率。
     */
    private String reinforceLanguage(String question) {
        if (containsCjk(question) || question.contains("中文")) {
            return "请严格使用简体中文回答。\n\n用户问题：" + question;
        }
        return question;
    }

    /**
     * 判断文本是否包含中日韩字符。
     */
    private boolean containsCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Web 上传的图片附件。
     */
    public record ImageAttachment(String fileName, String mimeType, String contentBase64) {
        public ImageAttachment {
            fileName = fileName == null ? "" : fileName;
            mimeType = mimeType == null ? "" : mimeType;
            contentBase64 = contentBase64 == null ? "" : contentBase64;
        }

        /**
         * 返回可用于 data URI 的图片 MIME 类型。
         */
        public String safeMimeType() {
            return mimeType == null || !mimeType.startsWith("image/") ? "image/png" : mimeType;
        }
    }

    /**
     * 多模态流开始信息。
     */
    public record MultimodalStreamStart(String model, int imageCount) {
    }

    /**
     * 多模态回答结果。
     */
    public record MultimodalAnswer(String model, String answer) {
    }

    /**
     * 多模态流式回调。
     */
    public interface MultimodalStreamHandler {
        /**
         * 请求已开始。
         */
        void onStarted(MultimodalStreamStart start);

        /**
         * 模型输出的文本增量。
         */
        void onToken(String token);

        /**
         * 请求已完成。
         */
        void onDone(MultimodalAnswer answer);
    }
}
