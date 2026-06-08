package com.aster.llm.common;

import java.util.Set;

/**
 * 模型供应商的通用定义。
 *
 * <p>不同供应商可能只支持 chat，也可能同时支持 embedding、语音或图片。
 * 这个接口只描述供应商和能力，不绑定具体 HTTP 协议。</p>
 */
public interface ModelProviderDefinition {
    /**
     * 供应商名称。
     */
    String name();

    /**
     * 供应商支持的模型能力集合。
     */
    default Set<ModelCapability> capabilities() {
        return Set.of(ModelCapability.CHAT_COMPLETIONS);
    }
}
