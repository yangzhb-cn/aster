package dev.agentmvp.context.model;

import dev.agentmvp.llm.model.Message;

import java.util.List;

/**
 * ContextBuilder 的输出结果。
 *
 * <p>这里不只返回最终消息列表，也返回压缩前后的估算 token，
 * 方便教学和调试时看清楚：这次构造上下文到底有没有触发压缩。</p>
 */
public record ContextBuildResult(
        List<Message> messages,
        boolean compressed,
        int beforeTokens,
        int afterTokens,
        String summary
) {
}
