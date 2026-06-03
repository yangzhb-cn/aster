package com.aster;

import com.aster.ui.tui.render.TerminalTextSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 终端文本清理测试。
 */
class TerminalTextSanitizerTest {
    /**
     * 验证 TUI 显示层会过滤 emoji，而不是替换成其它符号。
     */
    @Test
    void removesEmojiForTerminalDisplay() {
        TerminalTextSanitizer sanitizer = new TerminalTextSanitizer();

        assertEquals(
                "你好  继续",
                sanitizer.sanitize("你好 😊☺ 继续")
        );
        assertEquals(
                "启动  完成",
                sanitizer.sanitize("启动 🚀 完成")
        );
    }
}
