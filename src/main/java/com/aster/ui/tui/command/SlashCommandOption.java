package com.aster.ui.tui.command;

/**
 * 斜杠菜单里的一个可选命令。
 *
 * <p>label 用来显示，insertText 用来写回输入框。
 * requiresInput=true 表示这个命令还需要用户继续输入参数。</p>
 */
public record SlashCommandOption(String label, String insertText, String description, boolean requiresInput) {
}
