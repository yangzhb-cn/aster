package dev.agentmvp.core.hook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hook 注册中心。
 *
 * <p>它把“流程插槽”和“扩展逻辑”解耦。AgentLoop 只知道触发某个 HookPoint，
 * 不知道后面挂了长期记忆、工具权限、工具结果卸载还是别的扩展。</p>
 */
public class HookRegistry {
    private final Map<String, List<HookHandler<?, ?>>> handlers = new LinkedHashMap<>();

    /**
     * 创建空 Hook 注册中心。
     */
    public static HookRegistry empty() {
        return new HookRegistry();
    }

    /**
     * 给指定 HookPoint 注册一个处理器。
     */
    public <C, R> void register(HookPoint<C, R> point, HookHandler<C, R> handler) {
        Objects.requireNonNull(point);
        Objects.requireNonNull(handler);
        handlers.computeIfAbsent(point.name(), ignored -> new ArrayList<>()).add(handler);
    }

    /**
     * 执行“输入上下文 -> 输出上下文”的管道型 Hook。
     *
     * <p>典型场景：before_llm_request 逐个改写 messages/tools，
     * before_tool_result_append 逐个改写即将写入 role=tool 的文本。</p>
     */
    public <C> C apply(HookPoint<C, C> point, C context) throws IOException {
        C current = context;
        for (HookHandler<C, C> handler : handlers(point)) {
            current = handler.handle(current);
        }
        return current;
    }

    /**
     * 执行工具调用决策型 Hook。
     *
     * <p>只要某个 Hook 返回非 ALLOW，就立即停止后续 Hook。
     * 这样高危工具审批、权限拒绝可以短路执行链。</p>
     */
    public <C> ToolHookDecision decide(HookPoint<C, ToolHookDecision> point, C context) throws IOException {
        for (HookHandler<C, ToolHookDecision> handler : handlers(point)) {
            ToolHookDecision decision = handler.handle(context);
            if (decision.type() != ToolHookDecisionType.ALLOW) {
                return decision;
            }
        }
        return ToolHookDecision.allow();
    }

    /**
     * 执行通知型 Hook，并吞掉单个 Hook 的 IOException。
     *
     * <p>after_run 常用于提交后台任务。这里不让后台 Hook 失败影响主回答返回。</p>
     */
    public <C> void fireQuietly(HookPoint<C, Void> point, C context) {
        for (HookHandler<C, Void> handler : handlers(point)) {
            try {
                handler.handle(context);
            } catch (IOException ignored) {
                // 后台 Hook 失败不能影响主流程。
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <C, R> List<HookHandler<C, R>> handlers(HookPoint<C, R> point) {
        return (List<HookHandler<C, R>>) (List<?>) handlers.getOrDefault(point.name(), List.of());
    }
}
