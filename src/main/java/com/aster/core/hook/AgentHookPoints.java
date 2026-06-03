package com.aster.core.hook;

/**
 * Agent 主流程当前开放的 Hook 点。
 *
 * <p>这些名字和事件流生命周期保持一致：Event 用来通知“发生了什么”，
 * Hook 用来在对应生命周期边界上改写、阻断或追加动作。</p>
 */
public final class AgentHookPoints {
    private AgentHookPoints() {
    }

    /**
     * LLM 请求前：可以注入长期记忆、追加临时系统提示、过滤工具列表。
     */
    public static final HookPoint<BeforeLlmRequestContext, BeforeLlmRequestContext> BEFORE_LLM_REQUEST =
            new HookPoint<>("before_llm_request");

    /**
     * 工具执行前：可以做权限判断、高危工具拒绝或后续 HITL 审批。
     */
    public static final HookPoint<BeforeToolCallContext, ToolHookDecision> BEFORE_TOOL_CALL =
            new HookPoint<>("before_tool_call");

    /**
     * 工具结果写入 session 前：可以做大结果卸载、脱敏、裁剪。
     */
    public static final HookPoint<BeforeToolResultAppendContext, BeforeToolResultAppendContext> BEFORE_TOOL_RESULT_APPEND =
            new HookPoint<>("before_tool_result_append");

    /**
     * 一次用户请求结束后：可以提交后台任务，例如长期记忆抽取。
     */
    public static final HookPoint<AfterRunContext, Void> AFTER_RUN =
            new HookPoint<>("after_run");
}
