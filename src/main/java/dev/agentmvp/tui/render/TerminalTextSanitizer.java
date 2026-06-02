package dev.agentmvp.tui.render;

/**
 * 终端显示文本清理器。
 *
 * <p>Lanterna 3.x 的部分绘制路径会按 char 处理字符串。
 * supplementary-plane emoji 需要两个 Java char 表示，容易被拆成两个问号。
 * 所以这里只在“显示层”直接过滤 emoji：内部消息、发给 LLM 的内容仍保留原始文本。</p>
 */
public class TerminalTextSanitizer {
    /**
     * 转成当前 TUI 更稳定的显示文本。
     */
    public String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);

            if (isVariationSelector(codePoint)) {
                continue;
            }
            if (isEmojiLike(codePoint)) {
                continue;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    private boolean isVariationSelector(int codePoint) {
        return codePoint == 0xFE0E || codePoint == 0xFE0F;
    }

    private boolean isEmojiLike(int codePoint) {
        // 常见 emoji 和 pictograph 都在这些补充平面区间里。
        if (codePoint >= 0x1F000 && codePoint <= 0x1FAFF) {
            return true;
        }
        // 兼容旧 fallback 或模型直接输出的 BMP 表情。
        return codePoint == 0x263A   // ☺
                || codePoint == 0x2639  // ☹
                || codePoint == 0x263B; // ☻
    }
}
