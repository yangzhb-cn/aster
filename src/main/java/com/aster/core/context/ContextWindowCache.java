package com.aster.core.context;

import com.aster.core.context.model.ContextBuildResult;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.context.model.Turn;
import com.aster.core.context.model.TurnType;
import com.aster.core.session.SessionStore;
import com.aster.llm.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 运行态上下文窗口缓存。
 *
 * <p>SessionStore 仍然保存完整 JSONL 历史；本缓存只保存当前 runtime 发请求所需的
 * “旧对话摘要 + 最近完整 turn”。它避免每轮 LLM 请求都从 JSONL 全量回放，
 * 同时按 user turn 边界压缩，防止切断 assistant.tool_calls 和 role=tool。</p>
 */
public class ContextWindowCache {
    private final TokenEstimator tokenEstimator;
    private final Summarizer summarizer;
    private final ContextOptions options;
    private final List<Message> systemMessages = new ArrayList<>();
    private final List<Turn> recentTurns = new ArrayList<>();

    private String runningSummary;
    private int processedMessageCount;

    public ContextWindowCache(
            TokenEstimator tokenEstimator,
            Summarizer summarizer,
            ContextOptions options
    ) {
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator);
        this.summarizer = Objects.requireNonNull(summarizer);
        this.options = Objects.requireNonNull(options);
    }

    /**
     * 从完整 session 初始化运行态窗口。
     *
     * <p>这一步只在 runtime 打开 session 时执行一次。后续新增消息通过
     * {@link #append(Message)} 增量进入缓存，不再依赖每轮请求全量 replay。</p>
     */
    public static ContextWindowCache from(
            SessionStore sessionStore,
            TokenEstimator tokenEstimator,
            Summarizer summarizer,
            ContextOptions options
    ) throws IOException {
        ContextWindowCache cache = new ContextWindowCache(tokenEstimator, summarizer, options);
        cache.initialize(sessionStore.loadMessages());
        return cache;
    }

    /**
     * 初始化缓存内容。
     */
    public synchronized void initialize(List<Message> messages) {
        systemMessages.clear();
        recentTurns.clear();
        runningSummary = null;
        processedMessageCount = 0;
        for (Message message : messages) {
            appendInternal(message);
            trimIfNeeded();
        }
    }

    /**
     * 增量追加一条已经成功写入 SessionStore 的消息。
     */
    public synchronized void append(Message message) {
        appendInternal(message);
        trimIfNeeded();
    }

    /**
     * 构造本轮可发送给 LLM 的上下文。
     */
    public synchronized ContextBuildResult build() {
        List<Message> messages = currentRequestMessages();
        ToolProtocolValidator.validate(messages);

        int afterTokens = tokenEstimator.estimate(messages);
        int beforeTokens = estimateWindowTokens();
        return new ContextBuildResult(
                messages,
                runningSummary != null && !runningSummary.isBlank(),
                beforeTokens,
                afterTokens,
                options.maxContextTokens(),
                runningSummary
        );
    }

    /**
     * 当前缓存已经处理的消息数量，主要用于测试和调试。
     */
    public synchronized int processedMessageCount() {
        return processedMessageCount;
    }

    private void appendInternal(Message message) {
        Objects.requireNonNull(message);
        processedMessageCount++;
        if ("system".equals(message.role())) {
            systemMessages.add(message);
            return;
        }
        if ("user".equals(message.role())) {
            recentTurns.add(new Turn(TurnType.USER_TURN, List.of(message)));
            return;
        }
        if (recentTurns.isEmpty()) {
            mergeIntoSummary(List.of(new Turn(TurnType.ORPHAN, List.of(message))));
            return;
        }
        int lastIndex = recentTurns.size() - 1;
        recentTurns.set(lastIndex, appendToTurn(recentTurns.get(lastIndex), message));
    }

    private Turn appendToTurn(Turn turn, Message message) {
        List<Message> messages = new ArrayList<>(turn.messages());
        messages.add(message);
        return new Turn(turn.type(), List.copyOf(messages));
    }

    private void trimIfNeeded() {
        int threshold = (int) (options.maxContextTokens() * options.compressThreshold());
        if (estimateWindowTokens() < threshold) {
            return;
        }

        int keepTurnCount = options.keepRecentTurns() + 1;
        if (recentTurns.size() <= keepTurnCount) {
            return;
        }

        int flushCount = recentTurns.size() - keepTurnCount;
        List<Turn> flushed = new ArrayList<>(recentTurns.subList(0, flushCount));
        recentTurns.subList(0, flushCount).clear();
        mergeIntoSummary(flushed);
    }

    private void mergeIntoSummary(List<Turn> turns) {
        List<Message> summaryInput = new ArrayList<>();
        if (runningSummary != null && !runningSummary.isBlank()) {
            summaryInput.add(Message.user("已有旧对话摘要：\n" + runningSummary));
        }
        summaryInput.addAll(flatten(turns));
        if (!summaryInput.isEmpty()) {
            runningSummary = summarizer.summarize(summaryInput);
        }
    }

    private int estimateWindowTokens() {
        List<Message> messages = new ArrayList<>(currentRequestMessages());
        if (runningSummary != null && !runningSummary.isBlank()) {
            messages.add(Message.user(runningSummary));
        }
        return tokenEstimator.estimate(messages);
    }

    private List<Message> currentRequestMessages() {
        List<Message> messages = new ArrayList<>(systemMessages);
        messages.addAll(flatten(recentTurns));
        return List.copyOf(messages);
    }

    private List<Message> flatten(List<Turn> turns) {
        List<Message> messages = new ArrayList<>();
        for (Turn turn : turns) {
            messages.addAll(turn.messages());
        }
        return messages;
    }
}
