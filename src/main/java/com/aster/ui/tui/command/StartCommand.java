package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;

import java.util.List;

/**
 * 执行当前待确认 Plan 的命令。
 */
public class StartCommand implements SlashCommand {
    private static final String COMMAND = "/start";
    private static final SlashCommandOption OPTION = new SlashCommandOption(
            COMMAND,
            COMMAND,
            "执行当前 Plan",
            false
    );

    @Override
    public List<SlashCommandOption> options() {
        return List.of(OPTION);
    }

    @Override
    public boolean matches(String input) {
        return COMMAND.equals(input);
    }

    @Override
    public void handle(SlashCommandContext context, String input) {
        AgentRuntime runtime = context.window().runtime();
        if (runtime == null) {
            context.window().addErrorBlock("runtime is not ready");
            context.window().setStatus("runtime is not ready");
            return;
        }

        boolean started = runtime.startPlan();
        context.window().addSystemBlock(started ? "Plan 开始执行。" : "当前没有待执行的 Plan。");
        context.window().setStatus(started ? "plan running" : "no pending plan");
    }
}
