package dev.agentmvp;

import dev.agentmvp.tui.render.MarkdownLineType;
import dev.agentmvp.tui.render.MarkdownRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轻量 Markdown 渲染器测试。
 */
class MarkdownRendererTest {
    /**
     * 验证标题、引用、代码块和表格会被识别成不同的行类型。
     */
    @Test
    void rendersCommonMarkdownLineTypes() {
        MarkdownRenderer renderer = new MarkdownRenderer();

        var lines = renderer.render("""
                ## 项目说明
                > 引用内容
                ```java
                System.out.println("hi");
                ```
                | 模块 | 说明 |
                | --- | --- |
                | agent | 主循环 |
                1. 读取 `pom.xml`
                - 新增功能 😊
                ---
                """, 80);

        assertEquals(MarkdownLineType.HEADING, lines.get(0).type());
        assertEquals("项目说明", lines.get(0).text());
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.QUOTE));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.CODE));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.TABLE));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.LIST && line.text().contains("pom.xml")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.LIST && line.text().startsWith("• 新增功能")));
        assertTrue(lines.stream().noneMatch(line -> line.text().contains("??")));
        assertTrue(lines.stream().noneMatch(line -> line.text().contains("😊")));
        assertTrue(lines.stream().noneMatch(line -> line.text().contains("☺")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.RULE));
    }
}
