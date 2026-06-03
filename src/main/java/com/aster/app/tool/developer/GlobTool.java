package com.aster.app.tool.developer;

import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * glob 扩展工具。
 *
 * <p>按文件名模式查找文件，返回按修改时间倒序排列的匹配路径。</p>
 */
public class GlobTool extends AbstractDeveloperTool {
    private static final int MAX_RESULTS = 100;
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "__pycache__", ".venv", "venv", ".tox", "dist", "build", "target"
    );

    public GlobTool(Path workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "glob",
                "Glob",
                "按 glob pattern 查找文件，适合根据文件名或扩展名定位代码文件。",
                objectSchema(
                        Map.of(
                                "pattern", stringSchema("用于匹配文件的 glob pattern，例如 \"**/*.java\""),
                                "path", stringSchema("要搜索的目录。默认当前工作目录")
                        ),
                        List.of("pattern")
                )
        );
    }

    /**
     * 在指定目录下查找匹配文件。
     */
    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        String pattern = requiredString(arguments, "pattern");
        Path base = resolvePath(optionalString(arguments, "path", "."));
        if (!Files.isDirectory(base)) {
            return ToolResult.error(call.id(), "搜索路径不是目录: " + displayPath(base));
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (var walk = Files.walk(base)) {
            List<Path> hits = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !isSkipped(path))
                    .filter(path -> matches(base, path, matcher))
                    .sorted(Comparator.comparingLong(this::mtime).reversed())
                    .limit(MAX_RESULTS + 1)
                    .toList();
            if (hits.isEmpty()) {
                return ToolResult.text(call.id(), "没有匹配的文件。");
            }
            String text = String.join("\n", hits.stream()
                    .limit(MAX_RESULTS)
                    .map(this::displayPath)
                    .toList());
            if (hits.size() > MAX_RESULTS) {
                text += "\n... (超过 " + MAX_RESULTS + " 个匹配，仅显示前 " + MAX_RESULTS + " 个)";
            }
            return ToolResult.text(call.id(), text);
        }
    }

    private boolean matches(Path base, Path path, PathMatcher matcher) {
        Path relative = base.relativize(path);
        return matcher.matches(relative) || matcher.matches(path.getFileName());
    }

    private boolean isSkipped(Path path) {
        for (Path part : path) {
            if (SKIP_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private long mtime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
