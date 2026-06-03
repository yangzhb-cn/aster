package com.aster.ui.tui.command;

import com.aster.ui.tui.AgentTuiWindow;

import java.util.Objects;

/**
 * 斜杠命令执行上下文。
 *
 * <p>命令通过它访问 TUI 窗口公开的安全操作，例如追加系统消息、切换 session、
 * 请求 runtime stop 或 steer。</p>
 */
public record SlashCommandContext(AgentTuiWindow window) {
    public SlashCommandContext {
        Objects.requireNonNull(window);
    }
}
