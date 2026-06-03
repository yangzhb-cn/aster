package com.aster.app.tool.developer;

import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * grep 扩展工具。
 *
 * <p>用 Java 正则搜索文件内容，返回 file:line: text 形式的匹配行。</p>
 */
public class GrepTool extends AbstractDeveloperTool {
    private static final int MAX_FILES = 5_000;
    private static final int MAX_MATCHES = 200;
    private static final long MAX_FILE_BYTES = 2_000_000;
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "__pycache__", ".venv", "venv", ".tox", "dist", "build", "target"
    );

    public GrepTool(Path workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "grep",
                "GREP",
                "用正则表达式搜索文件内容，最多返回 200 条 file:line 匹配。",
                objectSchema(
                        Map.of(
                                "pattern", stringSchema("用于搜索文件内容的正则表达式"),
                                "path", stringSchema("要搜索的文件或目录。默认当前工作目录"),
                                "include", stringSchema("只搜索匹配该 glob 的文件，例如 \"*.java\" 或 \"**/*.md\"")
                        ),
                        List.of("pattern")
                )
        );
    }

    /**
     * 搜索指定文件或目录里的文本匹配。
     */
    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        Pattern regex;
        try {
            regex = Pattern.compile(requiredString(arguments, "pattern"));
        } catch (PatternSyntaxException e) {
            return ToolResult.error(call.id(), "正则表达式无效: " + e.getMessage());
        }

        Path base = resolvePath(optionalString(arguments, "path", "."));
        if (!Files.exists(base)) {
            return ToolResult.error(call.id(), "路径不存在: " + displayPath(base));
        }

        List<Path> files = Files.isRegularFile(base)
                ? List.of(base)
                : walkFiles(base, optionalString(arguments, "include", ""));
        List<String> matches = new ArrayList<>();
        for (Path file : files) {
            searchFile(regex, file, matches);
            if (matches.size() >= MAX_MATCHES) {
                matches.add("... (已达到 " + MAX_MATCHES + " 条匹配上限)");
                break;
            }
        }

        return ToolResult.text(call.id(), matches.isEmpty() ? "未找到匹配。" : String.join("\n", matches));
    }

    private List<Path> walkFiles(Path root, String include) throws Exception {
        PathMatcher matcher = include == null || include.isBlank()
                ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + include);
        try (var walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !isSkipped(path))
                    .filter(path -> matcher == null || matches(root, path, matcher))
                    .limit(MAX_FILES)
                    .toList();
        }
    }

    private void searchFile(Pattern regex, Path file, List<String> matches) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    matches.add(displayPath(file) + ":" + (i + 1) + ": " + lines.get(i).stripTrailing());
                    if (matches.size() >= MAX_MATCHES) {
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // 二进制文件或不可读文件直接跳过，避免搜索被单个文件打断。
        }
    }

    private boolean matches(Path root, Path path, PathMatcher matcher) {
        Path relative = root.relativize(path);
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
}
