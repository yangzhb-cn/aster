package dev.agentmvp.core.stage;

import dev.agentmvp.core.context.ContextBuilder;
import dev.agentmvp.core.context.model.ContextBuildResult;
import dev.agentmvp.llm.model.Message;

import java.util.List;
import java.util.Objects;

/**
 * 上下文压缩和协议校验内置 Stage。
 *
 * <p>具体压缩规则仍由 ContextBuilder 负责：按 user turn 边界切分，
 * 保留最近轮次，把旧轮次摘要，并在最终发送前校验 tool_call/tool_result 协议。</p>
 */
public class ContextCompressionStage implements Stage {
    private final ContextBuilder contextBuilder;

    public ContextCompressionStage(ContextBuilder contextBuilder) {
        this.contextBuilder = Objects.requireNonNull(contextBuilder);
    }

    @Override
    public String name() {
        return "context_compression";
    }

    /**
     * 从完整 session 历史构造本轮可发送上下文。
     */
    public ContextBuildResult build(List<Message> sessionMessages) {
        return contextBuilder.build(sessionMessages);
    }
}
