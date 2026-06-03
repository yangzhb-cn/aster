package com.aster.core.context;

import com.aster.core.context.model.ContextBuildResult;
import com.aster.core.session.SessionStore;
import com.aster.core.stage.ContextCompressionStage;
import com.aster.core.stage.LoadSessionMessagesStage;
import com.aster.core.stage.StagePipeline;

import java.io.IOException;
import java.util.Objects;

/**
 * LLM 请求上下文的内置 Stage 流水线。
 *
 * <p>它不是普通 Hook。每次请求 LLM 前都必须经过这里：
 * 先从 SessionStore 读取完整历史，再进入上下文压缩和工具协议校验。
 * 这样 AgentLoop 只负责编排，不直接关心上下文怎么安全构造。</p>
 */
public class ContextPipeline implements StagePipeline<ContextBuildResult> {
    private final LoadSessionMessagesStage loadSessionMessagesStage;
    private final ContextCompressionStage contextCompressionStage;

    public ContextPipeline(SessionStore sessionStore, ContextBuilder contextBuilder) {
        this(
                new LoadSessionMessagesStage(sessionStore),
                new ContextCompressionStage(contextBuilder)
        );
    }

    public ContextPipeline(
            LoadSessionMessagesStage loadSessionMessagesStage,
            ContextCompressionStage contextCompressionStage
    ) {
        this.loadSessionMessagesStage = Objects.requireNonNull(loadSessionMessagesStage);
        this.contextCompressionStage = Objects.requireNonNull(contextCompressionStage);
    }

    @Override
    public String name() {
        return "context_pipeline";
    }

    /**
     * 执行上下文内置 Stage 流水线。
     */
    @Override
    public ContextBuildResult run() throws IOException {
        return contextCompressionStage.build(loadSessionMessagesStage.load());
    }

    /**
     * 保留语义更直观的入口，调用方读起来会更像“构造本轮上下文”。
     */
    public ContextBuildResult build() throws IOException {
        return run();
    }
}
