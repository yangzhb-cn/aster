package dev.agentmvp.llm;

/**
 * DeepSeek 模型名常量。
 *
 * <p>先把 deepseek-v4-flash 固定在这里，后续接其他 OpenAI 协议模型时，
 * 可以新增供应商配置或新增模型常量，不需要改 AgentLoop。</p>
 */
public final class DeepSeekModels {
    public static final String V4_FLASH = "deepseek-v4-flash";

    private DeepSeekModels() {
    }
}
