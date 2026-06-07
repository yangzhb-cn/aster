package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;
import com.aster.app.team.model.TeamRunRequest;

import java.util.List;

/**
 * 启动固定 DAG Agent Team 探索的命令。
 */
public class TeamCommand implements SlashCommand {
    private static final String PREFIX = "/team";
    private static final SlashCommandOption OPTION = new SlashCommandOption(
            "/team",
            "/team ",
            "启动只读 Agent Team 探索",
            true
    );

    @Override
    public List<SlashCommandOption> options() {
        return List.of(OPTION);
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

        String raw = input.length() <= PREFIX.length() ? "" : input.substring(PREFIX.length()).trim();
        TeamRunRequest request = TeamRunRequest.parse(raw);
        if (request.task().isBlank()) {
            context.window().addErrorBlock("用法：/team [--model 模型名] 要探索的问题");
            context.window().setStatus("team command requires task");
            return;
        }

        String selectedModel = runtime.submitTeam(request.task(), request.model());
        context.window().addSystemBlock("Agent Team 探索开始：" + request.task() + "\nmodel=" + selectedModel);
        context.window().setStatus("agent team running");
    }
}
