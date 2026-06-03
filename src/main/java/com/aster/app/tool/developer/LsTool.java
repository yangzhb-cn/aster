package com.aster.app.tool.developer;

import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;

/**
 * ls 扩展工具。
 *
 * <p>列出目录的直接子项，适合先观察项目结构；开放式文件查找应优先使用 glob。</p>
 */
public class LsTool extends AbstractDeveloperTool {
    private static final int MAX_ENTRIES = 500;

    public LsTool(Path workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "ls",
                "LS",
                "列出目录下的文件和子目录。支持相对路径、绝对路径和 ignore glob patterns。",
                objectSchema(
                        Map.of(
                                "path", stringSchema("要列出的目录路径。相对路径按当前工作目录解析"),
                                "ignore", stringArraySchema("要忽略的 glob patterns 列表，例如 [\"target\", \"*.log\"]")
                        ),
                        List.of("path")
                )
        );
    }

    /**
     * 列出目录项并按文件名排序。
     */
    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        Path directory = resolvePath(requiredString(arguments, "path"));
        if (!Files.exists(directory)) {
            return ToolResult.error(call.id(), "目录不存在: " + displayPath(directory));
        }
        if (!Files.isDirectory(directory)) {
            return ToolResult.error(call.id(), "ls 只能列出目录: " + displayPath(directory));
        }

        List<String> ignore = stringList(arguments.get("ignore"));
        try (var stream = Files.list(directory)) {
            List<String> entries = stream
                    .filter(path -> !ignored(directory, path, ignore))
                    .sorted()
                    .limit(MAX_ENTRIES + 1)
                    .map(path -> path.getFileName() + (Files.isDirectory(path) ? "/" : ""))
                    .toList();
            if (entries.isEmpty()) {
                return ToolResult.text(call.id(), "(空目录)");
            }
            String text = String.join("\n", entries.stream().limit(MAX_ENTRIES).toList());
            if (entries.size() > MAX_ENTRIES) {
                text += "\n... (超过 " + MAX_ENTRIES + " 项，仅显示前 " + MAX_ENTRIES + " 项)";
            }
            return ToolResult.text(call.id(), text);
        }
    }

    private boolean ignored(Path root, Path path, List<String> ignore) {
        Path name = path.getFileName();
        Path relative = root.relativize(path);
        for (String pattern : ignore) {
            PathMatcher matcher = path.getFileSystem().getPathMatcher("glob:" + pattern);
            if (matcher.matches(name) || matcher.matches(relative)) {
                return true;
            }
        }
        return false;
    }
}
