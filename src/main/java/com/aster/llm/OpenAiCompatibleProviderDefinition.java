package com.aster.llm;

import com.aster.llm.model.OpenAiCompatibleProvider;

/**
 * OpenAI 兼容模型供应商的定义接口。
 *
 * <p>DeepSeek、Kimi、Step 这类供应商只是默认 baseUrl、模型名、
 * API Key 环境变量不同。AgentLoop 不应该依赖某个具体供应商，
 * 只需要拿到最终的 OpenAiCompatibleProvider 配置。</p>
 */
public interface OpenAiCompatibleProviderDefinition {
    /**
     * 供应商名称。
     */
    String name();

    /**
     * 默认 OpenAI 兼容 baseUrl。
     */
    String defaultBaseUrl();

    /**
     * 默认模型名。
     */
    String defaultModel();

    /**
     * 供应商自己的 API Key 环境变量名。
     */
    String apiKeyEnvName();

    /**
     * 是否默认打开 thinking mode。
     *
     * <p>并不是所有 OpenAI-compatible 供应商都支持这个字段。
     * 默认关闭，只有明确支持的供应商实现类才打开。</p>
     */
    default boolean thinkingEnabled() {
        return false;
    }

    /**
     * thinking mode 的默认推理强度。
     */
    default String reasoningEffort() {
        return null;
    }

    /**
     * 根据 API Key 创建运行时供应商配置。
     */
    default OpenAiCompatibleProvider toProvider(String apiKey) {
        return new OpenAiCompatibleProvider(
                name(),
                defaultBaseUrl(),
                apiKey,
                defaultModel(),
                thinkingEnabled(),
                reasoningEffort()
        );
    }
}
