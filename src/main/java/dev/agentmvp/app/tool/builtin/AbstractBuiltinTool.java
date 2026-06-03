package dev.agentmvp.app.tool.builtin;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 内置工具基类。
 *
 * <p>这里放所有内置工具共享的小能力：路径解析、展示路径、JSON Schema 构造。
 * 具体工具只关心自己的参数和执行逻辑。</p>
 */
public abstract class AbstractBuiltinTool implements BuiltinTool {
    protected final Path workingDirectory;

    protected AbstractBuiltinTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    /**
     * 解析路径。
     *
     * <p>相对路径按工作目录解析，绝对路径原样使用；这里不做访问范围限制。</p>
     */
    protected Path resolvePath(String rawPath) {
        Path input = Path.of(rawPath);
        return input.isAbsolute()
                ? input.toAbsolutePath().normalize()
                : workingDirectory.resolve(input).normalize();
    }

    /**
     * 展示路径。
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

    protected static Map<String, Object> arraySchema(String description, Map<String, Object> itemProperties, List<String> required) {
        return Map.of(
                "type", "array",
                "description", description,
                "items", objectSchema(itemProperties, required)
        );
    }
}
