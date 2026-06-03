package com.aster.ui.tui.command;

import java.io.IOException;
import java.util.List;

/**
 * TUI 斜杠命令。
 *
 * <p>命令对象只处理命令输入，不直接参与 Agent 主循环。
 * 这样新增命令时不需要继续扩大 AgentTuiWindow。</p>
 */
public interface SlashCommand {
    /**
     * 当前命令暴露给菜单的选项。
     */
    List<SlashCommandOption> options();

    /**
     * 判断当前输入是否由这个命令处理。
     */
    boolean matches(String input);

    /**
     * 执行命令。
     */
    void handle(SlashCommandContext context, String input) throws IOException;
}
