package com.aster.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.model.OpenAiCompatibleProvider;
import okhttp3.OkHttpClient;

import java.util.List;

/**
 * DeepSeek 供应商定义。
 *
 * <p>它只是 OpenAI 兼容供应商的一个例子。后续新增 Kimi、Step 等供应商时，
 * 只需要再实现 OpenAiCompatibleProviderDefinition，不需要改 AgentLoop。</p>
 */
public class DeepSeekProvider implements OpenAiCompatibleProviderDefinition {
    public static final String NAME = "deepseek";
    public static final String BASE_URL = "https://api.deepseek.com";
    public static final String API_KEY_ENV = "DEEPSEEK_API_KEY";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String defaultBaseUrl() {
        return BASE_URL;
    }

    @Override
    public String defaultModel() {
        return DeepSeekModels.V4_FLASH;
    }

    @Override
    public String apiKeyEnvName() {
        return API_KEY_ENV;
    }

    /**
     * DeepSeek 当前允许用户切换 flash / pro。
     */
    @Override
    public List<String> switchableChatModels() {
        return DeepSeekModels.switchableChatModels();
    }

    /**
     * DeepSeek V4 支持 thinking mode，教学版默认打开。
     */
    @Override
    public boolean thinkingEnabled() {
        return true;
    }

    /**
     * 教学版默认使用 high，让“深度思考”展示更明显。
     */
    @Override
    public String reasoningEffort() {
        return "high";
    }

    /**
     * 从 DeepSeek 官方环境变量创建供应商配置。
     */
    public static OpenAiCompatibleProvider fromEnv() {
        return new DeepSeekProvider().toProvider(System.getenv(API_KEY_ENV));
    }

    /**
     * 创建 DeepSeek 供应商配置，默认模型使用 deepseek-v4-flash。
     */
    public static OpenAiCompatibleProvider fromApiKey(String apiKey) {
        return new DeepSeekProvider().toProvider(apiKey);
    }

    /**
     * 创建 DeepSeek 的流式客户端。
     */
    public static OkHttpStreamingChatClient createClient(OkHttpClient httpClient, ObjectMapper objectMapper) {
        return OpenAiCompatibleChatClient.create(httpClient, objectMapper, fromEnv());
    }
}
