package com.aster.ui.tui.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 斜杠命令注册表。
 *
 * <p>它负责菜单筛选和命令查找。AgentTuiWindow 只关心输入分发，
 * 不再硬编码每个命令的处理细节。</p>
 */
public class SlashCommandRegistry {
    private final List<SlashCommand> commands;

    public SlashCommandRegistry(List<SlashCommand> commands) {
        this.commands = List.copyOf(Objects.requireNonNull(commands));
    }

    /**
     * 创建默认 TUI 命令集合。
     */
    public static SlashCommandRegistry defaults() {
        return new SlashCommandRegistry(List.of(
                new ExitCommand(),
                new SessionCommand(),
                new TeamCommand(),
                new SteerCommand(),
                new StopCommand(),
                new ApproveCommand(),
                new DenyCommand()
        ));
    }

    /**
     * 查找能处理当前输入的命令。
     */
    public Optional<SlashCommand> find(String input) {
        return commands.stream()
                .filter(command -> command.matches(input))
                .findFirst();
    }

    /**
     * 根据当前输入筛选可展示的命令菜单。
     */
    public List<SlashCommandOption> visibleOptions(String typed) {
        if (!typed.startsWith("/")) {
            return List.of();
        }

        List<SlashCommandOption> matches = new ArrayList<>();
        for (SlashCommand command : commands) {
            for (SlashCommandOption option : command.options()) {
                if (matchesOption(option, typed)) {
                    matches.add(option);
                }
            }
        }
        return List.copyOf(matches);
    }

    private boolean matchesOption(SlashCommandOption option, String typed) {
        if (typed.equals("/")) {
            return true;
        }
        if (option.requiresInput()) {
            return typed.length() <= option.insertText().length()
                    && option.insertText().startsWith(typed);
        }
        return option.insertText().startsWith(typed);
    }
}
