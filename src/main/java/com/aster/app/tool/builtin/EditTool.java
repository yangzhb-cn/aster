package com.aster.app.tool.builtin;

import com.aster.llm.text.model.ToolCall;
import com.aster.app.tool.builtin.model.EditToolParams;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * edit 工具：用精确文本替换编辑文件。
 *
 * <p>支持一次传入多个 replacements。每个 oldText 必须唯一匹配，
 * 多个替换区域不能重叠。</p>
 */
public class EditTool extends AbstractBuiltinTool {
    public EditTool(Path workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "edit",
                "Edit",
                "通过精确文本替换编辑文件，支持一次修改多个不重叠区域。",
                objectSchema(
                        Map.of(
                                "path", stringSchema("要编辑的文件路径。相对路径按工作目录解析，绝对路径原样使用"),
                                "oldText", stringSchema("单区域编辑时使用：文件中必须精确存在的原文"),
                                "newText", stringSchema("单区域编辑时使用：替换后的新内容"),
                                "replacements", arraySchema(
                                        "多区域编辑时使用：每项包含 oldText 和 newText",
                                        Map.of(
                                                "oldText", stringSchema("文件中必须精确存在的原文"),
                                                "newText", stringSchema("替换后的新内容")
                                        ),
                                        List.of("oldText", "newText")
                                )
                        ),
                        List.of("path")
                )
        );
    }

    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        EditToolParams params = EditToolParams.from(arguments);
        Path path = resolvePath(params.path());

        if (!Files.exists(path)) {
            return ToolResult.error(call.id(), "文件不存在: " + params.path());
        }

        String original = Files.readString(path, StandardCharsets.UTF_8);
        List<ReplacementMatch> matches = findReplacementMatches(original, params.replacements());
        String updated = applyReplacements(original, matches);

        Files.writeString(path, updated, StandardCharsets.UTF_8);
        return ToolResult.text(call.id(), "已编辑文件: " + displayPath(path) + "，替换次数: " + matches.size());
    }

    private List<ReplacementMatch> findReplacementMatches(String original, List<EditToolParams.Replacement> replacements) {
        List<ReplacementMatch> matches = new ArrayList<>();
        for (EditToolParams.Replacement replacement : replacements) {
            int count = countOccurrences(original, replacement.oldText());
            if (count == 0) {
                throw new IllegalArgumentException("没有找到 oldText，未修改文件: " + preview(replacement.oldText()));
            }
            if (count > 1) {
                throw new IllegalArgumentException("oldText 出现 " + count + " 次，请提供更精确的 oldText: " + preview(replacement.oldText()));
            }

            int start = original.indexOf(replacement.oldText());
            matches.add(new ReplacementMatch(start, start + replacement.oldText().length(), replacement.newText()));
        }

        matches.sort(Comparator.comparingInt(ReplacementMatch::start));
        for (int i = 1; i < matches.size(); i++) {
            if (matches.get(i - 1).end() > matches.get(i).start()) {
                throw new IllegalArgumentException("多个编辑区域发生重叠，未修改文件");
            }
        }
        return matches;
    }

    private String applyReplacements(String original, List<ReplacementMatch> matches) {
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (ReplacementMatch match : matches) {
            result.append(original, cursor, match.start());
            result.append(match.newText());
            cursor = match.end();
        }
        result.append(original.substring(cursor));
        return result.toString();
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String preview(String text) {
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }

    /**
     * edit 匹配到的替换区域。
     */
    private record ReplacementMatch(int start, int end, String newText) {
    }
}
