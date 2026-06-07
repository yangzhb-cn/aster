package com.aster.ui.tui.command;

import com.aster.app.runtime.AgentRuntime;

import java.util.List;

/**
 * 切换当前 Chat 模型的命令。
 *
 * <p>它只修改当前 TUI session 绑定 runtime 的 chat 模型，不影响摘要器、工具和 session 存储。</p>
 */
public class ModelCommand implements SlashCommand {
    private static final String PREFIX = "/model";
    private static final SlashCommandOption OPTION = new SlashCommandOption(
            "/model <name>",
            "/model ",
            "切换当前 Chat 模型",
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

        String model = input.length() <= PREFIX.length() ? "" : input.substring(PREFIX.length()).trim();
        if (model.isBlank()) {
            context.window().addSystemBlock("当前模型：" + runtime.chatModel()
                    + "\n可选模型：" + String.join(", ", runtime.availableChatModels()));
            context.window().setStatus("model: " + runtime.chatModel());
            return;
        }

        String selected = runtime.switchChatModel(model);
        context.window().addSystemBlock("已切换模型：" + selected);
        context.window().setStatus("model: " + selected);
    }
}
