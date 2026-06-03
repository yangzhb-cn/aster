package dev.agentmvp.core.context.model;

/**
 * Turn 的类型。
 *
 * <p>SYSTEM 单独保留，USER_TURN 是正常对话轮次，ORPHAN 表示历史里出现了
 * 不在 user 轮次下的 assistant/tool 消息，构造器会把它们作为一组异常尾巴处理。</p>
 */
public enum TurnType {
    SYSTEM,
    USER_TURN,
    ORPHAN
}
