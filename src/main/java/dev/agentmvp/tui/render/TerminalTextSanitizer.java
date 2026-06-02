package dev.agentmvp.tui.render;

/**
 * 终端显示文本清理器。
 *
 * <p>Lanterna 3.x 的部分绘制路径会按 char 处理字符串。
 * supplementary-plane emoji 需要两个 Java char 表示，容易被拆成两个问号。
 * 所以这里只在“显示层”做 fallback：内部消息、发给 LLM 的内容仍保留原始文本。</p>
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
            if (codePoint <= Character.MAX_VALUE) {
                result.appendCodePoint(codePoint);
                continue;
            }

            result.append(fallbackForSupplementary(codePoint));
        }
        return result.toString();
    }

    private boolean isVariationSelector(int codePoint) {
        return codePoint == 0xFE0E || codePoint == 0xFE0F;
    }

    private String fallbackForSupplementary(int codePoint) {
        return switch (codePoint) {
            case 0x1F44B -> "~";      // waving hand
            case 0x1F44D -> "√";      // thumbs up
            case 0x1F389 -> "★";      // party popper
            case 0x1F525 -> "火";     // fire
            case 0x1F680 -> "▲";      // rocket
            case 0x1F4A1 -> "灯";     // light bulb
            case 0x1F4DD -> "记";     // memo
            default -> fallbackByRange(codePoint);
        };
    }

    private String fallbackByRange(int codePoint) {
        if (codePoint >= 0x1F600 && codePoint <= 0x1F64F) {
            return "☺";
        }
        if (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) {
            return "◆";
        }
        if (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) {
            return "▲";
        }
        if (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) {
            return "◇";
        }
        if (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF) {
            return "◇";
        }
        return "□";
    }
}
