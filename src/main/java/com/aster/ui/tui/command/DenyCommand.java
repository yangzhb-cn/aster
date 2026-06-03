package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;

import java.util.List;

/**
 * 拒绝待审批工具调用的命令。
 */
public class DenyCommand implements SlashCommand {
    private static final SlashCommandOption OPTION = new SlashCommandOption(
            "/deny [id] [reason]",
            "/deny ",
            "拒绝待审批工具，不填 id 表示全部拒绝",
            true
    );

    @Override
    public List<SlashCommandOption> options() {
        return List.of(OPTION);
    }

    @Override
    public boolean matches(String input) {
        return input.equals("/deny") || input.startsWith("/deny ");
    }

    @Override
    public void handle(SlashCommandContext context, String input) {
        AgentRuntime runtime = context.window().runtime();
        if (runtime == null) {
            context.window().addErrorBlock("runtime is not ready");
            context.window().setStatus("runtime is not ready");
            return;
        }

        String rest = input.length() <= "/deny".length()
                ? ""
                : input.substring("/deny".length()).trim();
        if (rest.isEmpty()) {
            int count = runtime.denyAllTools("用户拒绝全部待审批工具");
            context.window().setStatus("denied tools: " + count);
            if (count == 0) {
                context.window().addErrorBlock("no pending tool approval");
            }
            return;
        }

        String[] parts = rest.split("\\s+", 2);
        String approvalId = parts[0];
        String reason = parts.length > 1 ? parts[1] : "用户拒绝执行";
        if (runtime.denyTool(approvalId, reason)) {
            context.window().setStatus("tool denied");
        } else {
            context.window().addErrorBlock("approval not found: " + approvalId);
            context.window().setStatus("deny failed");
        }
    }
}
