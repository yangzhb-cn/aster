package com.aster.ui.tui.render;

/**
 * Markdown 渲染后的行类型。
 *
 * <p>TUI 是字符界面，不适合做完整富文本。
 * 这里先把 Markdown 解析成“行级语义”，再由 AgentTuiWindow 决定颜色和样式。</p>
 */
public enum MarkdownLineType {
    NORMAL,
    HEADING,
    QUOTE,
    CODE,
    TABLE,
    RULE,
    LIST,
    BLANK
}
