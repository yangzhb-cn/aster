package dev.agentmvp.core.tool;

import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.core.tool.model.Tool;
import dev.agentmvp.core.tool.model.ToolResult;

/**
 * 某一种工具来源的执行策略。
 */
public interface ToolExecutor {
    /**
     * 执行一次工具调用，并始终返回能和 LLM 调用 id 配对的结果。
     */
    ToolResult execute(Tool tool, ToolCall call);
}
