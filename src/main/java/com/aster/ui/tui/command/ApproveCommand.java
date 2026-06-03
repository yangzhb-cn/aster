package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;

import java.util.List;

/**
 * 批准待审批工具调用的命令。
 */
public class ApproveCommand implements SlashCommand {
    private static final SlashCommandOption OPTION = new SlashCommandOption(
            "/approve [id]",
            "/approve ",
            "批准待审批工具，不填 id 表示全部批准",
            true
    );

    @Override
    public List<SlashCommandOption> options() {
        return List.of(OPTION);
    }

    @Override
    public boolean matches(String input) {
        return input.equals("/approve") || input.startsWith("/approve ");
    }

    @Override
    public void handle(SlashCommandContext context, String input) {
        AgentRuntime runtime = context.window().runtime();
        if (runtime == null) {
            context.window().addErrorBlock("runtime is not ready");
            context.window().setStatus("runtime is not ready");
            return;
        }

        String approvalId = input.length() <= "/approve".length()
                ? ""
                : input.substring("/approve".length()).trim();
        if (approvalId.isEmpty()) {
            int count = runtime.approveAllTools();
            context.window().setStatus("approved tools: " + count);
            if (count == 0) {
                context.window().addErrorBlock("no pending tool approval");
            }
            return;
        }

        if (runtime.approveTool(approvalId)) {
            context.window().setStatus("tool approved");
        } else {
            context.window().addErrorBlock("approval not found: " + approvalId);
            context.window().setStatus("approve failed");
        }
    }
}
