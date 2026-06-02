package dev.agentmvp.context;

import dev.agentmvp.llm.model.Message;
import dev.agentmvp.context.model.ContextBuildResult;
import dev.agentmvp.context.model.ContextOptions;
import dev.agentmvp.context.model.Turn;
import dev.agentmvp.context.model.TurnType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 构造一次请求要发给 LLM 的安全消息列表。
 *
 * <p>SessionStore 保存完整原始会话。ContextBuilder 是唯一决定
 * “发请求前是否压缩旧轮次”的地方。</p>
 */
public class ContextBuilder {
    private final TokenEstimator tokenEstimator;
    private final Summarizer summarizer;
    private final ContextOptions options;

    public ContextBuilder(TokenEstimator tokenEstimator, Summarizer summarizer, ContextOptions options) {
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator);
        this.summarizer = Objects.requireNonNull(summarizer);
        this.options = Objects.requireNonNull(options);
    }

    /**
     * 从完整 session 历史构造可直接发给 LLM 的上下文。
     */
    public ContextBuildResult build(List<Message> sessionMessages) {
        // 这里用估算器而不是直接数消息条数。
        // 真正的上下文限制来自 token，不来自消息条数；MVP 用 SimpleTokenEstimator，
        // 生产环境可以替换为模型对应 tokenizer。
        int beforeTokens = tokenEstimator.estimate(sessionMessages);
        int threshold = (int) (options.maxContextTokens() * options.compressThreshold());

        if (beforeTokens < threshold) {
            // 即使不压缩，也要校验协议。因为历史可能来自恢复的 session，
            // 里面可能已经存在孤立工具结果或残留 tool_calls。
            ToolProtocolValidator.validate(sessionMessages);
            return new ContextBuildResult(
                    List.copyOf(sessionMessages),
                    false,
                    beforeTokens,
                    beforeTokens,
                    options.maxContextTokens(),
                    null
            );
        }

        // 压缩前先按 user turn 切块。
        // 关键目的不是“方便摘要”，而是防止切断：
        // assistant.tool_calls -> tool.tool_call_id 这组协议消息。
        List<Turn> turns = buildTurns(sessionMessages);
        List<Message> systemMessages = new ArrayList<>();
        List<Turn> userTurns = new ArrayList<>();
        List<Turn> orphanTurns = new ArrayList<>();

        for (Turn turn : turns) {
            if (turn.type() == TurnType.SYSTEM) {
                systemMessages.addAll(turn.messages());
            } else if (turn.type() == TurnType.USER_TURN) {
                userTurns.add(turn);
            } else {
                // orphan 不能原样保留，因为它可能只是工具协议配对中的半截消息。
                orphanTurns.add(turn);
            }
        }

        int splitIndex = Math.max(0, userTurns.size() - options.keepRecentTurns());
        List<Turn> compressHead = new ArrayList<>();
        // orphan 不进最终原文，只能进入摘要。它们没有明确 user 归属，
        // 原样保留很容易变成“半截 tool 协议”。
        compressHead.addAll(orphanTurns);
        // 早期完整 turn 被压缩；最近 N 个完整 turn 原样保留。
        // 这是常见的“摘要 + 滑动窗口”组合。
        compressHead.addAll(userTurns.subList(0, splitIndex));

        List<Turn> keepTail = userTurns.subList(splitIndex, userTurns.size());
        List<Message> summaryInput = flatten(compressHead);

        List<Message> finalMessages = new ArrayList<>();
        finalMessages.addAll(systemMessages);

        String summary = null;
        if (!summaryInput.isEmpty()) {
            summary = summarizer.summarize(summaryInput);

            // 摘要必须重建成一条干净的普通消息，不能克隆旧 assistant 消息。
            // 否则旧 tool_calls 可能残留，而对应工具结果已经被摘要吃掉。
            // 这里用 user 角色是为了兼容大多数 OpenAI 兼容 Chat API。
            // 语义上它不是用户的新请求，所以 content 里明确写“系统自动生成的历史摘要”。
            finalMessages.add(Message.user(
                    "以下是系统自动生成的历史对话摘要，不是用户的新请求：\n\n" + summary
            ));
        }

        finalMessages.addAll(flatten(keepTail));
        // 这是最后一道门：任何要发给 LLM 的消息列表，都必须先通过协议校验。
        ToolProtocolValidator.validate(finalMessages);

        return new ContextBuildResult(
                List.copyOf(finalMessages),
                true,
                beforeTokens,
                tokenEstimator.estimate(finalMessages),
                options.maxContextTokens(),
                summary
        );
    }

    /**
     * 按 user turn 边界切分原始消息，避免把工具协议切成半截。
     */
    List<Turn> buildTurns(List<Message> messages) {
        List<Turn> turns = new ArrayList<>();
        List<Message> current = null;

        for (Message message : messages) {
            if ("system".equals(message.role())) {
                // system 提示词不属于某个用户轮次，单独保留。
                // 压缩时通常保留所有 system，因为它们控制模型行为。
                turns.add(new Turn(TurnType.SYSTEM, List.of(message)));
                continue;
            }

            if ("user".equals(message.role())) {
                // user 是 turn 边界。遇到新的 user，上一轮就结束。
                if (current != null) {
                    turns.add(new Turn(TurnType.USER_TURN, current));
                }

                current = new ArrayList<>();
                current.add(message);
                continue;
            }

            if (current == null) {
                // 历史可能从一个已经被切坏的边界恢复出来，这种块不能原样保留。
                // 例如一段历史开头就是工具结果，这说明前面的 assistant tool_call 不在窗口里。
                // 这种消息不能原样发给 LLM，只能进入摘要或被丢弃。
                turns.add(new Turn(TurnType.ORPHAN, List.of(message)));
                continue;
            }

            // assistant 回复、assistant tool_calls 和 tool 结果都留在同一个 user turn 里。
            current.add(message);
        }

        if (current != null) {
            turns.add(new Turn(TurnType.USER_TURN, current));
        }

        return turns;
    }

    private List<Message> flatten(List<Turn> turns) {
        List<Message> messages = new ArrayList<>();
        for (Turn turn : turns) {
            messages.addAll(turn.messages());
        }
        return messages;
    }
}
