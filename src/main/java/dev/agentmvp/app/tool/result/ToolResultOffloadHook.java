package dev.agentmvp.app.tool.result;

import dev.agentmvp.core.hook.BeforeToolResultAppendContext;
import dev.agentmvp.core.hook.HookHandler;

import java.io.IOException;
import java.util.Objects;

/**
 * 工具结果外部卸载 Hook。
 *
 * <p>它挂在 before_tool_result_append 阶段：工具已经执行完，
 * 但 role=tool 消息还没有写入 session。大结果会被写入 JSONL，
 * tool 消息里只留下路径、recordId 和预览，避免上下文被大输出撑爆。</p>
 */
public class ToolResultOffloadHook implements HookHandler<BeforeToolResultAppendContext, BeforeToolResultAppendContext> {
    private final ToolResultOffloader offloader;

    public ToolResultOffloadHook(ToolResultOffloader offloader) {
        this.offloader = Objects.requireNonNull(offloader);
    }

    @Override
    public BeforeToolResultAppendContext handle(BeforeToolResultAppendContext context) throws IOException {
        return context.withToolMessageText(offloader.compact(context.toolResult()));
    }
}
