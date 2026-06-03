package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;

import java.util.List;

/**
 * 生成或取消动态 DAG Plan 的命令。
 */
public class PlanCommand implements SlashCommand {
    private static final String PREFIX = "/plan";
    private static final SlashCommandOption PLAN_OPTION = new SlashCommandOption(
            "/plan",
            "/plan ",
            "生成动态 DAG 计划",
            true
    );
    private static final SlashCommandOption CANCEL_OPTION = new SlashCommandOption(
            "/plan cancel",
            "/plan cancel",
            "取消当前 Plan",
            false
    );

    @Override
    public List<SlashCommandOption> options() {
        return List.of(PLAN_OPTION, CANCEL_OPTION);
    }

    @Override
    public boolean matches(String input) {
        return input.equals(PREFIX) || input.startsWith(PREFIX + " ");
    }

    @Override
    public void handle(SlashCommandContext context, String input) {
        AgentRuntime runtime = context.window().runtime();
        if (runtime == null) {
            context.window().addErrorBlock("runtime is not ready");
            context.window().setStatus("runtime is not ready");
            return;
        }

        String task = input.length() <= PREFIX.length() ? "" : input.substring(PREFIX.length()).trim();
        if ("cancel".equalsIgnoreCase(task)) {
            boolean canceled = runtime.cancelPlan();
            context.window().addSystemBlock(canceled ? "Plan 已取消。" : "当前没有可取消的 Plan。");
            context.window().setStatus(canceled ? "plan canceled" : "no plan");
            return;
        }
        if (task.isBlank()) {
            context.window().addErrorBlock("用法：/plan 要完成的任务");
            context.window().setStatus("plan command requires task");
            return;
        }

        runtime.submitPlan(task);
        context.window().addSystemBlock("Plan 生成中：" + task);
        context.window().setStatus("planning");
    }
}
