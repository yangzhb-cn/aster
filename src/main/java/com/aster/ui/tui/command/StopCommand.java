package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;

import java.util.List;

/**
 * 停止当前 Agent run 的命令。
 */
public class StopCommand implements SlashCommand {
    private static final SlashCommandOption OPTION = new SlashCommandOption("/stop", "/stop", "停止当前 Agent 输出", false);

    @Override
    public List<SlashCommandOption> options() {
        return List.of(OPTION);
    }

    @Override
    public boolean matches(String input) {
        return "/stop".equals(input);
    }

    @Override
    public void handle(SlashCommandContext context, String input) {
        AgentRuntime runtime = context.window().runtime();
        if (runtime == null) {
            context.window().addErrorBlock("runtime is not ready");
            context.window().setStatus("runtime is not ready");
            return;
        }
        if (runtime.stop()) {
            context.window().setStatus("stop requested");
        } else {
            context.window().addErrorBlock("agent is not running");
            context.window().setStatus("stop failed");
        }
    }
}
