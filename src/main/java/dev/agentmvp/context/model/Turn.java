package dev.agentmvp.context.model;

import dev.agentmvp.llm.model.Message;

import java.util.List;

/**
 * 按协议边界切出来的一段消息。
 *
 * <p>一个 USER_TURN 从 user 消息开始，后面可以跟 assistant tool_calls
 * 和工具结果。压缩时按 Turn 移动，就不会把 tool_call 和 tool_result 切散。</p>
 */
public record Turn(TurnType type, List<Message> messages) {
}
