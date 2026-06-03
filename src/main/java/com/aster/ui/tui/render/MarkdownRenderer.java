package com.aster.ui.tui.render;

import com.googlecode.lanterna.TerminalTextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 教学版轻量 Markdown 渲染器。
 *
 * <p>它不是完整 CommonMark 实现，只覆盖 TUI 中最常见、最有价值的格式：
 * 标题、引用、分隔线、代码块、表格、编号/列表、行内 code 和粗体标记清理。
 * 这样不引入额外依赖，也能让模型输出比纯文本更容易读。</p>
 */
public class MarkdownRenderer {
    private static final Pattern ORDERED_LIST = Pattern.compile("^(\\d+)[.)]\\s+(.*)$");
    private static final Pattern UNORDERED_LIST = Pattern.compile("^[-*+]\\s+(.*)$");

    private final TerminalTextSanitizer textSanitizer = new TerminalTextSanitizer();

    /**
     * 把 Markdown 文本转换成行级渲染结果。
     */
    public List<MarkdownLine> render(String markdown, int width) {
        String normalized = textSanitizer.sanitize(nullToEmpty(markdown))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);

        List<MarkdownLine> result = new ArrayList<>();
        boolean inCodeBlock = false;
        List<String> tableBuffer = new ArrayList<>();

        for (String rawLine : rawLines) {
            String trimmed = rawLine.trim();

            if (trimmed.startsWith("```")) {
                flushTable(result, tableBuffer, width);
                inCodeBlock = !inCodeBlock;
                continue;
            }

            if (inCodeBlock) {
                result.add(new MarkdownLine(rawLine, MarkdownLineType.CODE));
                continue;
            }

            if (looksLikeTableRow(trimmed)) {
                tableBuffer.add(rawLine);
                continue;
            }

            flushTable(result, tableBuffer, width);
            appendNormalMarkdownLine(result, rawLine, trimmed, width);
        }

        flushTable(result, tableBuffer, width);
        return result;
    }

    private void appendNormalMarkdownLine(List<MarkdownLine> result, String rawLine, String trimmed, int width) {
        if (trimmed.isEmpty()) {
            result.add(MarkdownLine.blank());
            return;
        }

        if (isRule(trimmed)) {
            result.add(new MarkdownLine("-".repeat(Math.max(3, width)), MarkdownLineType.RULE));
            return;
        }

        if (trimmed.startsWith(">")) {
            String quote = trimmed.replaceFirst("^>\\s?", "");
            result.add(new MarkdownLine("| " + cleanInlineMarkdown(quote), MarkdownLineType.QUOTE));
            return;
        }

        if (trimmed.matches("^#{1,6}\\s+.*")) {
            String heading = trimmed.replaceFirst("^#{1,6}\\s+", "");
            result.add(new MarkdownLine(cleanInlineMarkdown(heading), MarkdownLineType.HEADING));
            return;
        }

        Matcher orderedList = ORDERED_LIST.matcher(trimmed);
        if (orderedList.matches()) {
            result.add(new MarkdownLine(
                    orderedList.group(1) + ". " + cleanInlineMarkdown(orderedList.group(2)),
                    MarkdownLineType.LIST
            ));
            return;
        }

        Matcher unorderedList = UNORDERED_LIST.matcher(trimmed);
        if (unorderedList.matches()) {
            result.add(new MarkdownLine(
                    "• " + cleanInlineMarkdown(unorderedList.group(1)),
                    MarkdownLineType.LIST
            ));
            return;
        }

        result.add(new MarkdownLine(cleanInlineMarkdown(rawLine), MarkdownLineType.NORMAL));
    }

    private boolean looksLikeTableRow(String trimmed) {
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.indexOf('|', 1) > 0;
    }

    private void flushTable(List<MarkdownLine> result, List<String> tableBuffer, int width) {
        if (tableBuffer.isEmpty()) {
            return;
        }

        List<List<String>> rows = tableBuffer.stream()
                .map(this::parseTableRow)
                .filter(row -> !isSeparatorRow(row))
                .toList();
        tableBuffer.clear();

        if (rows.isEmpty()) {
            return;
        }

        int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
        int[] naturalWidths = new int[columnCount];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                naturalWidths[i] = Math.max(naturalWidths[i], TerminalTextUtils.getColumnWidth(row.get(i)));
            }
        }
        int[] widths = fitTableWidths(naturalWidths, width);

        result.add(new MarkdownLine(tableBorder(widths, '┌', '┬', '┐'), MarkdownLineType.TABLE));
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            for (String line : formatWrappedTableRow(rows.get(rowIndex), widths)) {
                result.add(new MarkdownLine(line, MarkdownLineType.TABLE));
            }
            if (rowIndex < rows.size() - 1) {
                result.add(new MarkdownLine(tableBorder(widths, '├', '┼', '┤'), MarkdownLineType.TABLE));
            }
        }
        result.add(new MarkdownLine(tableBorder(widths, '└', '┴', '┘'), MarkdownLineType.TABLE));
    }

    private List<String> parseTableRow(String line) {
        String trimmed = line.trim();
        String withoutEdges = trimmed.substring(1, trimmed.length() - 1);
        String[] cells = withoutEdges.split("\\|", -1);

        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(cleanInlineMarkdown(cell.trim()));
        }
        return result;
    }

    private boolean isSeparatorRow(List<String> row) {
        return !row.isEmpty() && row.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    /**
     * 根据 TUI 可用宽度压缩表格列宽，避免整行表格被终端外层折断。
     */
    private int[] fitTableWidths(int[] naturalWidths, int width) {
        int columnCount = naturalWidths.length;
        if (columnCount == 0) {
            return naturalWidths;
        }
        if (tableWidth(naturalWidths) <= width) {
            return naturalWidths;
        }

        int contentBudget = Math.max(columnCount, width - 1 - 3 * columnCount);
        int minWidth = contentBudget >= columnCount * 3 ? 3 : 1;
        int[] widths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            widths[i] = Math.min(Math.max(naturalWidths[i], 1), minWidth);
        }

        int remaining = contentBudget - Arrays.stream(widths).sum();
        while (remaining > 0) {
            int target = widestDeficitColumn(naturalWidths, widths);
            if (target < 0) {
                break;
            }
            widths[target]++;
            remaining--;
        }
        return widths;
    }

    /**
     * 找出最需要继续分配宽度的列。
     */
    private int widestDeficitColumn(int[] naturalWidths, int[] widths) {
        int target = -1;
        int largestDeficit = 0;
        for (int i = 0; i < naturalWidths.length; i++) {
            int deficit = naturalWidths[i] - widths[i];
            if (deficit > largestDeficit) {
                largestDeficit = deficit;
                target = i;
            }
        }
        return target;
    }

    /**
     * 计算当前列宽对应的整张表格终端列宽。
     */
    private int tableWidth(int[] widths) {
        return 1 + Arrays.stream(widths).map(width -> width + 3).sum();
    }

    /**
     * 把一行 Markdown 表格渲染成一行或多行 TUI 表格文本。
     */
    private List<String> formatWrappedTableRow(List<String> row, int[] widths) {
        List<List<String>> cells = new ArrayList<>();
        int rowHeight = 1;
        for (int i = 0; i < widths.length; i++) {
            String cell = i < row.size() ? row.get(i) : "";
            List<String> wrapped = wrapCell(cell, widths[i]);
            cells.add(wrapped);
            rowHeight = Math.max(rowHeight, wrapped.size());
        }

        List<String> lines = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < rowHeight; lineIndex++) {
            StringBuilder line = new StringBuilder("│");
            for (int columnIndex = 0; columnIndex < widths.length; columnIndex++) {
                List<String> cellLines = cells.get(columnIndex);
                String cell = lineIndex < cellLines.size() ? cellLines.get(lineIndex) : "";
                line.append(' ')
                        .append(padRight(cell, widths[columnIndex]))
                        .append(" │");
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * 按终端列宽折行单元格内容。
     */
    private List<String> wrapCell(String value, int width) {
        if (value == null || value.isEmpty()) {
            return List.of("");
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            String text = new String(Character.toChars(codePoint));
            int textWidth = TerminalTextUtils.getColumnWidth(text);
            if (currentWidth > 0 && currentWidth + textWidth > width) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }

            current.append(text);
            currentWidth += textWidth;

            if (currentWidth >= width) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }
            index += Character.charCount(codePoint);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    /**
     * 渲染表格边框或行分隔线。
     */
    private String tableBorder(int[] widths, char left, char middle, char right) {
        StringBuilder result = new StringBuilder();
        result.append(left);
        for (int width : widths) {
            result.append("─".repeat(width + 2)).append(middle);
        }
        result.setCharAt(result.length() - 1, right);
        return result.toString();
    }

    private String padRight(String value, int width) {
        int padding = Math.max(0, width - TerminalTextUtils.getColumnWidth(value));
        return value + " ".repeat(padding);
    }

    private boolean isRule(String trimmed) {
        return trimmed.matches("^-{3,}$")
                || trimmed.matches("^_{3,}$")
                || trimmed.matches("^\\*{3,}$");
    }

    /**
     * 清理行内 Markdown 标记。
     *
     * <p>字符终端很难做同一行内的局部富文本。这里先把常见标记去掉：
     * `code` 显示为 code，**bold** 显示为 bold。emoji 会在 TerminalTextSanitizer 中过滤。</p>
     */
    private String cleanInlineMarkdown(String text) {
        return nullToEmpty(text)
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
