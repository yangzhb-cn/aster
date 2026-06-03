package com.aster.app.extension;

import com.aster.app.runtime.WorkspacePaths;
import com.aster.app.tool.result.ToolResultOffloadHook;
import com.aster.app.tool.result.ToolResultOffloader;
import com.aster.core.hook.AgentHookPoints;

/**
 * 工具结果处理扩展。
 *
 * <p>它把大工具结果卸载到 workspace/artifacts/tool-results，避免上下文被大结果塞满。</p>
 */
public class ToolResultExtension implements AsterRuntimeExtension {
    /**
     * 注册工具结果写入前的卸载 Hook。
     */
    @Override
    public void registerHooks(RuntimeExtensionContext context) {
        context.hookRegistry().register(
                AgentHookPoints.BEFORE_TOOL_RESULT_APPEND,
                new ToolResultOffloadHook(ToolResultOffloader.defaults(context.objectMapper(), WorkspacePaths.TOOL_RESULTS))
        );
    }
}
