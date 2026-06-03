package dev.agentmvp.hook;

import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.tool.model.ToolResult;

import java.util.Objects;

/**
 * 工具结果写入 session 前的 Hook 上下文。
 *
 * <p>Agent 已经执行完工具，并拿到了 ToolResult；但还没有把结果写成 role=tool 消息。
 * 这里允许 Hook 改写 toolMessageText，比如把大结果卸载到 JSONL 文件，只留下路径和预览。</p>
 */
public record BeforeToolResultAppendContext(
        String sessionName,
        String runId,
        ToolCall toolCall,
        ToolResult toolResult,
        String toolMessageText
) {
    public BeforeToolResultAppendContext {
        Objects.requireNonNull(sessionName);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(toolCall);
        Objects.requireNonNull(toolResult);
        Objects.requireNonNull(toolMessageText);
    }

    /**
     * 返回替换 tool 消息文本后的上下文。
     */
    public BeforeToolResultAppendContext withToolMessageText(String newToolMessageText) {
        return new BeforeToolResultAppendContext(
                sessionName,
                runId,
                toolCall,
                toolResult,
                newToolMessageText
        );
    }
}
