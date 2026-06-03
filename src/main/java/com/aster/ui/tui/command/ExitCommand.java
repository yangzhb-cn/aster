package com.aster.ui.tui.command;

import java.util.List;

/**
 * 退出 TUI 命令。
 */
public class ExitCommand implements SlashCommand {
    private static final SlashCommandOption OPTION = new SlashCommandOption("/exit", "/exit", "退出 TUI", false);

    @Override
    public List<SlashCommandOption> options() {
        return List.of(OPTION);
    }

    @Override
    public boolean matches(String input) {
        return "/exit".equals(input);
    }

    @Override
    public void handle(SlashCommandContext context, String input) {
        context.window().setStatus("exiting");
        context.window().close();
    }
}
