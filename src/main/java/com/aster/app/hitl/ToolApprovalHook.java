package com.aster.app.hitl;

import com.aster.core.hook.BeforeToolCallContext;
import com.aster.core.hook.HookHandler;
import com.aster.core.hook.ToolHookDecision;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 高危工具调用审批 Hook。
 *
 * <p>MVP 只保护 bash、write、edit。读取类和搜索类工具先不审批，
 * 避免把教学版正常观察项目的路径变得太重。</p>
 */
public class ToolApprovalHook implements HookHandler<BeforeToolCallContext, ToolHookDecision> {
    private static final Set<String> DEFAULT_PROTECTED_TOOLS = Set.of("bash", "write", "edit");
    private static final Pattern BASH_TIMING_COMMAND = Pattern.compile(
            "(?is)(^|[^a-z0-9_])(sleep\\s+\\d+|at\\s+|crontab\\b|nohup\\b|setsid\\b|disown\\b)"
    );

    private final ToolApprovalManager approvalManager;
    private final Set<String> protectedTools;

    public ToolApprovalHook(ToolApprovalManager approvalManager) {
        this(approvalManager, DEFAULT_PROTECTED_TOOLS);
    }

    public ToolApprovalHook(ToolApprovalManager approvalManager, Set<String> protectedTools) {
        this.approvalManager = Objects.requireNonNull(approvalManager);
        this.protectedTools = Set.copyOf(Objects.requireNonNull(protectedTools));
    }

    /**
     * 命中高危工具时阻塞等待人工审批，否则直接放行。
     */
    @Override
    public ToolHookDecision handle(BeforeToolCallContext context) throws IOException {
        String toolName = context.toolCall().function().name();
        if ("bash".equals(toolName) && isTimingCommand(context.toolCall().function().argumentsJson())) {
            return ToolHookDecision.deny(
                    "不允许用 bash sleep/at/crontab/nohup 等 shell 方式模拟提醒或定时任务；"
                            + "简单延时提醒请用 background_task reminder，需要 Agent 到点自动执行的任务请用 schedule。"
            );
        }
        if (!protectedTools.contains(toolName)) {
            return ToolHookDecision.allow();
        }
        return approvalManager.awaitApproval(context, "工具 " + toolName + " 需要人工审批");
    }

    /**
     * 判断 bash 参数里是否包含伪定时命令。
     */
    private boolean isTimingCommand(String argumentsJson) {
        return argumentsJson != null && BASH_TIMING_COMMAND.matcher(argumentsJson).find();
    }
}
