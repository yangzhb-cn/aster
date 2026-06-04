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
 * <p>它不是普通 Hook。每次请求 LLM 前都必须经过这里。
 * 主 runtime 使用 ContextWindowCache 增量维护“摘要 + 最近 turn”；
 * 旧构造器仍支持从 SessionStore 完整读取再压缩，方便测试和临时子 Agent 使用。
 * 这样 AgentLoop 只负责编排，不直接关心上下文怎么安全构造。</p>
 */
public class ContextPipeline implements StagePipeline<ContextBuildResult> {
    private final LoadSessionMessagesStage loadSessionMessagesStage;
    private final ContextCompressionStage contextCompressionStage;
    private final ContextWindowCache contextWindowCache;

    public ContextPipeline(SessionStore sessionStore, ContextBuilder contextBuilder) {
        this(
                new LoadSessionMessagesStage(sessionStore),
                new ContextCompressionStage(contextBuilder),
                null
        );
    }

    public ContextPipeline(ContextWindowCache contextWindowCache) {
        this(null, null, contextWindowCache);
    }

    public ContextPipeline(
            LoadSessionMessagesStage loadSessionMessagesStage,
            ContextCompressionStage contextCompressionStage
    ) {
        this(loadSessionMessagesStage, contextCompressionStage, null);
    }

    private ContextPipeline(
            LoadSessionMessagesStage loadSessionMessagesStage,
            ContextCompressionStage contextCompressionStage,
            ContextWindowCache contextWindowCache
    ) {
        if (contextWindowCache == null) {
            this.loadSessionMessagesStage = Objects.requireNonNull(loadSessionMessagesStage);
            this.contextCompressionStage = Objects.requireNonNull(contextCompressionStage);
            this.contextWindowCache = null;
        } else {
            this.loadSessionMessagesStage = null;
            this.contextCompressionStage = null;
            this.contextWindowCache = Objects.requireNonNull(contextWindowCache);
        }
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
        if (contextWindowCache != null) {
            return contextWindowCache.build();
        }
        return contextCompressionStage.build(loadSessionMessagesStage.load());
    }

    /**
     * 保留语义更直观的入口，调用方读起来会更像“构造本轮上下文”。
     */
    public ContextBuildResult build() throws IOException {
        return run();
    }
}
