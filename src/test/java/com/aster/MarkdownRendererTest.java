package com.aster;

import com.aster.ui.tui.render.MarkdownLineType;
import com.aster.ui.tui.render.MarkdownRenderer;
import com.googlecode.lanterna.TerminalTextUtils;
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
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.TABLE && line.text().startsWith("┌")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.TABLE && line.text().startsWith("├")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.TABLE && line.text().startsWith("└")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.TABLE && line.text().startsWith("│")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.LIST && line.text().contains("pom.xml")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.LIST && line.text().startsWith("• 新增功能")));
        assertTrue(lines.stream().noneMatch(line -> line.text().contains("??")));
        assertTrue(lines.stream().noneMatch(line -> line.text().contains("😊")));
        assertTrue(lines.stream().noneMatch(line -> line.text().contains("☺")));
        assertTrue(lines.stream().anyMatch(line -> line.type() == MarkdownLineType.RULE));
    }

    /**
     * 验证宽表格会在单元格内部折行，而不是生成超过 TUI 可用宽度的整行。
     */
    @Test
    void wrapsWideTableCellsWithinAvailableWidth() {
        MarkdownRenderer renderer = new MarkdownRenderer();

        var lines = renderer.render("""
                | 设计点 | 实现方式 | 原因 |
                | --- | --- | --- |
                | 流式唯一一路径 | 只保留 SSE 流式，无阻塞式调用 | 统一代码路径，降低维护成本 |
                | ContextPipeline 是 Stage 而非 Hook | 两阶段固定流水线 | 上下文构建是必经基础设施 |
                """, 42);

        var tableLines = lines.stream()
                .filter(line -> line.type() == MarkdownLineType.TABLE)
                .toList();

        assertTrue(tableLines.size() > 7);
        assertTrue(tableLines.stream().allMatch(line ->
                TerminalTextUtils.getColumnWidth(line.text()) <= 42
        ));
        assertTrue(tableLines.stream().noneMatch(line -> line.text().startsWith("+")));
    }
}
