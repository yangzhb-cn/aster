package com.aster.app.tool.developer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 开发者扩展工具基类。
 *
 * <p>这里只放工具之间共享的参数读取、路径解析和 JSON Schema 小工具，
 * 具体能力仍由每个工具类单独实现。</p>
 */
public abstract class AbstractDeveloperTool implements DeveloperTool {
    protected final Path workingDirectory;

    protected AbstractDeveloperTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    /**
     * 解析路径参数。
     *
     * <p>相对路径按当前工作目录解析，绝对路径原样使用，开头的 ~ 会展开到用户目录。</p>
     */
    protected Path resolvePath(String rawPath) {
        String value = rawPath == null || rawPath.isBlank() ? "." : rawPath.trim();
        if (value.startsWith("~")) {
            value = System.getProperty("user.home") + value.substring(1);
        }
        Path input = Path.of(value);
        return input.isAbsolute()
                ? input.toAbsolutePath().normalize()
                : workingDirectory.resolve(input).normalize();
    }

    /**
     * 把路径转成更适合工具结果展示的形式。
     */
    protected String displayPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(workingDirectory)) {
            return workingDirectory.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    protected static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", required
        );
    }

    protected static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    protected static Map<String, Object> numberSchema(String description) {
        return Map.of("type", "integer", "description", description);
    }

    protected static Map<String, Object> stringArraySchema(String description) {
        return Map.of(
                "type", "array",
                "description", description,
                "items", Map.of("type", "string")
        );
    }

    protected static String requiredString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("缺少必填字符串参数: " + name);
        }
        return text;
    }

    protected static String optionalString(Map<String, Object> arguments, String name, String defaultValue) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    protected static int optionalInt(Map<String, Object> arguments, String name, int defaultValue, int maxValue) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return Math.min(Math.max(number.intValue(), 0), maxValue);
        }
        return Math.min(Math.max(Integer.parseInt(String.valueOf(value)), 0), maxValue);
    }

    protected static List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            result.add(String.valueOf(item));
        }
        return List.copyOf(result);
    }

    protected static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n... (已截断到 " + maxChars + " 字符)";
    }
}
