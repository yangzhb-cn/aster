package com.aster.app.tool.builtin;

import com.aster.llm.text.model.ToolCall;
import com.aster.app.tool.builtin.model.WriteToolParams;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * write 工具：创建或覆盖文件。
 *
 * <p>父目录不存在时会自动创建。content 允许为空字符串，用来清空文件。</p>
 */
public class WriteTool extends AbstractBuiltinTool {
    public WriteTool(Path workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "write",
                "Write",
                "创建或覆盖文件，并自动创建父目录。",
                objectSchema(
                        Map.of(
                                "path", stringSchema("要写入的文件路径。相对路径按工作目录解析，绝对路径原样使用"),
                                "content", stringSchema("完整文件内容")
                        ),
                        List.of("path", "content")
                )
        );
    }

    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        WriteToolParams params = WriteToolParams.from(arguments);
        Path path = resolvePath(params.path());

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, params.content(), StandardCharsets.UTF_8);
        return ToolResult.text(call.id(), "已写入文件: " + displayPath(path));
    }
}
