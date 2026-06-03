package com.aster.app.extension;

import com.aster.app.hitl.ToolApprovalHook;
import com.aster.core.hook.AgentHookPoints;

/**
 * HITL 工具审批扩展。
 *
 * <p>它只通过 BEFORE_TOOL_CALL Hook 接入，不修改 bash/write/edit 的工具实现。</p>
 */
public class ToolApprovalExtension implements AsterRuntimeExtension {
    /**
     * 注册工具执行前审批 Hook。
     */
    @Override
    public void registerHooks(RuntimeExtensionContext context) {
        context.hookRegistry().register(
                AgentHookPoints.BEFORE_TOOL_CALL,
                new ToolApprovalHook(context.toolApprovalManager())
        );
    }
}
