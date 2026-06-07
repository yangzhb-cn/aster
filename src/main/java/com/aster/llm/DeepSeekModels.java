package com.aster.llm;

import java.util.List;

/**
 * DeepSeek 模型名常量。
 *
 * <p>当前教学版只开放 V4 flash/pro 两个 chat 模型。
 * 模型切换属于 runtime 状态，不应该写进 AgentLoop 的核心流程。</p>
 */
public final class DeepSeekModels {
    public static final String V4_FLASH = "deepseek-v4-flash";
    public static final String V4_PRO = "deepseek-v4-pro";

    private static final List<String> SWITCHABLE_CHAT_MODELS = List.of(V4_FLASH, V4_PRO);

    private DeepSeekModels() {
    }

    /**
     * 返回当前允许用户自由切换的 DeepSeek Chat 模型。
     */
    public static List<String> switchableChatModels() {
        return SWITCHABLE_CHAT_MODELS;
    }
}
