package com.aster.core.context;

import com.aster.core.context.model.ContextBuildResult;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.context.model.ContextWindowSnapshot;
import com.aster.core.context.model.Turn;
import com.aster.core.context.model.TurnType;
import com.aster.core.session.model.SessionMessageRecord;
import com.aster.core.session.SessionStore;
import com.aster.llm.model.Message;

import java.io.IOException;
import java.time.Instant;
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

    /**
     * 旧对话滚动摘要。
     *
     * <p>它不是 SessionStore 的一部分，也不会作为普通历史消息长期保存。
     * 每次旧 turn 被挤出窗口时，都会用“已有摘要 + 本次被挤出的 turn”
     * 重新生成一份新摘要。</p>
     */
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
     *
     * <p>runtime 打开已有 session 时，会把 JSONL 完整历史回放一次到缓存。
     * 回放过程中也会按阈值触发压缩，因此很长的历史不会全部留在
     * {@link #recentTurns} 里，而是逐步滚入 {@link #runningSummary}。</p>
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
     * 用启动消息和 JSONL message records 初始化缓存。
     *
     * <p>启动消息没有 JSONL seq/hash，但它们仍然参与上下文；records 则代表
     * session 文件中真正可见的用户/assistant/tool 历史。</p>
     */
    public synchronized void initialize(List<Message> bootstrapMessages, List<SessionMessageRecord> records) {
        List<Message> messages = new ArrayList<>(bootstrapMessages);
        messages.addAll(records.stream().map(SessionMessageRecord::message).toList());
        initialize(messages);
    }

    /**
     * 增量追加一条已经成功写入 SessionStore 的消息。
     *
     * <p>写入顺序很重要：先持久化原始消息，再更新运行态缓存。
     * 这样即使缓存逻辑只影响请求窗口，JSONL 仍然保留完整可审计历史。</p>
     */
    public synchronized void append(Message message) {
        appendInternal(message);
        trimIfNeeded();
    }

    /**
     * 构造本轮可发送给 LLM 的上下文。
     *
     * <p>返回的 messages 只包含 system 消息和最近 turn 原文；
     * {@link #runningSummary} 作为 summary 字段返回，交给请求前 Hook 注入
     * 最后一条 user 的 {@code <system-reminder>}，避免把摘要插到
     * assistant.tool_calls 和 role=tool 之间。</p>
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

    /**
     * 从快照恢复运行态窗口。
     *
     * <p>system prompt 使用当前启动时的内容，而不是快照里的旧内容。
     * 如果 system prompt 已经变化，外层会通过 hash 校验让快照失效。</p>
     */
    public synchronized void restore(List<Message> bootstrapMessages, ContextWindowSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        systemMessages.clear();
        systemMessages.addAll(bootstrapMessages);
        recentTurns.clear();
        recentTurns.addAll(copyTurns(snapshot.recentTurns()));
        runningSummary = snapshot.runningSummary();
        processedMessageCount = systemMessages.size() + countMessages(recentTurns);
        ToolProtocolValidator.validate(currentRequestMessages());
    }

    /**
     * 导出当前运行态窗口快照。
     */
    public synchronized ContextWindowSnapshot snapshot(
            String sessionId,
            String branchId,
            long lastSeq,
            String lastHash,
            String systemPromptHash,
            String summaryPromptHash,
            String summarizer,
            String model
    ) {
        return new ContextWindowSnapshot(
                ContextWindowSnapshot.CURRENT_VERSION,
                sessionId,
                branchId,
                lastSeq,
                lastHash,
                systemPromptHash,
                summaryPromptHash,
                summarizer,
                model,
                runningSummary,
                copyTurns(recentTurns),
                Instant.now().toString()
        );
    }

    /**
     * 把一条原始消息放入运行态窗口。
     *
     * <p>user 消息开启一个新 turn；assistant/tool 消息归到最近的 user turn。
     * 没有 user 边界的孤儿消息不能安全原样保留，只能滚入摘要。</p>
     */
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

    /**
     * 根据 token 阈值裁剪窗口。
     *
     * <p>窗口超阈值后，只把最早的完整 turn 挤出并摘要。
     * 当前 user turn 以及它前面的最近 N 个完整 turn 会原样保留。</p>
     */
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

    /**
     * 将被挤出窗口的 turn 合并进滚动摘要。
     *
     * <p>这是“摘要的摘要”策略：如果已经有 {@link #runningSummary}，
     * 本次摘要输入会先放入旧摘要，再追加新挤出的 turn。这样下一次请求仍能看到
     * 更早历史的压缩结果，但不会依赖上一轮临时注入过的 {@code <system-reminder>}。</p>
     */
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

    /**
     * 估算当前运行态窗口的 token。
     *
     * <p>虽然 {@link #runningSummary} 不直接放进 request messages，
     * 但它会在请求前注入给模型，所以这里必须把摘要也计入窗口压力。</p>
     */
    private int estimateWindowTokens() {
        List<Message> messages = new ArrayList<>(currentRequestMessages());
        if (runningSummary != null && !runningSummary.isBlank()) {
            messages.add(Message.user(runningSummary));
        }
        return tokenEstimator.estimate(messages);
    }

    /**
     * 生成请求消息的原文部分。
     *
     * <p>这里只返回 system + recent turns。旧对话摘要、长期记忆、当前时间等动态内容
     * 统一由 {@code BEFORE_LLM_REQUEST} Hook 临时注入。</p>
     */
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

    private List<Turn> copyTurns(List<Turn> turns) {
        List<Turn> copies = new ArrayList<>();
        for (Turn turn : turns) {
            copies.add(new Turn(turn.type(), List.copyOf(turn.messages())));
        }
        return List.copyOf(copies);
    }

    private int countMessages(List<Turn> turns) {
        int count = 0;
        for (Turn turn : turns) {
            count += turn.messages().size();
        }
        return count;
    }
}
