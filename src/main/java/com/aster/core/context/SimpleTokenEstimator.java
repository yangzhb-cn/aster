package com.aster.core.context;

import com.aster.llm.model.Message;
import com.aster.llm.model.ToolCall;

import java.util.List;

/**
 * MVP 使用的轻量 token 估算器。
 *
 * <p>这里故意只做近似估算。系统其他部分依赖的是 TokenEstimator 接口，
 * 以后可以替换成真实模型 tokenizer。</p>
 */
public class SimpleTokenEstimator implements TokenEstimator {
    /**
     * 根据消息文本和工具调用字段估算 token。
     */
    @Override
    public int estimate(List<Message> messages) {
        int chars = 0;

        for (Message message : messages) {
            chars += length(message.role());
            chars += length(message.content());
            chars += length(message.reasoningContent());
            chars += length(message.toolCallId());

            for (ToolCall call : message.toolCalls()) {
                chars += length(call.id());
                chars += length(call.function().name());
                chars += length(call.function().argumentsJson());
            }
        }

        // MVP 估算：英文和代码大约 4 个字符一个 token。真实项目可替换成模型 tokenizer。
        return Math.max(1, chars / 4);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
