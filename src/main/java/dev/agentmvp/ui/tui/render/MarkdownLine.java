package dev.agentmvp.ui.tui.render;

/**
 * Markdown 渲染后的单行内容。
 *
 * <p>这里只记录文本和行类型。颜色、背景、加粗等显示策略留给 TUI 窗口统一决定。</p>
 */
public record MarkdownLine(String text, MarkdownLineType type) {
    public static MarkdownLine blank() {
        return new MarkdownLine("", MarkdownLineType.BLANK);
    }
}
