package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;

import java.util.List;

/**
 * 运行中引导当前 Agent 的命令。
 */
public class SteerCommand implements SlashCommand {
    private static final SlashCommandOption OPTION = new SlashCommandOption("/steer <message>", "/steer ", "运行中引导当前 Agent", true);

    @Override
    public List<SlashCommandOption> options() {
        return List.of(OPTION);
    }

    @Override
    public boolean matches(String input) {
        return input.equals("/steer") || input.startsWith("/steer ");
    }

    @Override
    public void handle(SlashCommandContext context, String input) {
        AgentRuntime runtime = context.window().runtime();
        if (runtime == null) {
            context.window().addErrorBlock("runtime is not ready");
            context.window().setStatus("runtime is not ready");
            return;
        }

        String steerText = input.length() <= "/steer".length()
                ? ""
                : input.substring("/steer".length()).trim();
        if (steerText.isEmpty()) {
            context.window().addErrorBlock("usage: /steer <message>");
            context.window().setStatus("steer needs message");
            return;
        }

        if (runtime.steer(steerText)) {
            context.window().setStatus("steer sent");
        } else {
            context.window().addErrorBlock("agent is not running");
            context.window().setStatus("steer failed");
        }
    }
}
