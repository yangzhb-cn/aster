package com.aster.app.tool.builtin;

import com.aster.llm.model.ToolCall;
import com.aster.app.tool.builtin.model.ReadToolParams;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * read 工具：读取文件内容。
 *
 * <p>文本文件支持 offsetBytes、maxBytes、page、pageSizeBytes。
 * 图片文件会以 data URL 形式返回，方便模型把图片作为附件结果理解。</p>
 */
public class ReadTool extends AbstractBuiltinTool {
    public ReadTool(Path workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "read",
                "Read",
                "读取文件内容，支持文本分页读取和图片 data URL 返回。",
                objectSchema(
                        Map.of(
                                "path", stringSchema("要读取的文件路径。相对路径按工作目录解析，绝对路径原样使用"),
                                "offsetBytes", numberSchema("从第几个字节开始读取，默认 0"),
                                "maxBytes", numberSchema("最多读取多少字节，默认 200000"),
                                "page", numberSchema("页码，从 1 开始；设置后会覆盖 offsetBytes"),
                                "pageSizeBytes", numberSchema("分页读取时每页多少字节，默认 200000")
                        ),
                        List.of("path")
                )
        );
    }

    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws IOException {
        ReadToolParams params = ReadToolParams.from(arguments);
        Path path = resolvePath(params.path());

        if (!Files.exists(path)) {
            return ToolResult.error(call.id(), "文件不存在: " + params.path());
        }
        if (Files.isDirectory(path)) {
            return ToolResult.error(call.id(), "read 只能读取文件，不能读取目录: " + params.path());
        }

        String contentType = Files.probeContentType(path);
        if (contentType != null && contentType.startsWith("image/")) {
            return readImage(call, path, contentType, params.maxBytes());
        }

        byte[] bytes = readBytes(path, params.offsetBytes(), params.maxBytes());
        String text = new String(bytes, StandardCharsets.UTF_8);
        long fileSize = Files.size(path);
        String suffix = params.offsetBytes() + bytes.length < fileSize
                ? "\n\n[内容已截断，offsetBytes=" + params.offsetBytes() + "，returnedBytes=" + bytes.length + "，fileSize=" + fileSize + "]"
                : "";
        return ToolResult.text(call.id(), text + suffix);
    }

    private ToolResult readImage(ToolCall call, Path path, String contentType, int maxBytes) throws IOException {
        long size = Files.size(path);
        if (size > maxBytes) {
            return ToolResult.text(call.id(), """
                    图片文件过大，未返回 base64。
                    path=%s
                    mime=%s
                    size=%s bytes
                    maxBytes=%s
                    """.formatted(displayPath(path), contentType, size, maxBytes).stripTrailing());
        }

        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        return ToolResult.text(call.id(), """
                path=%s
                mime=%s
                size=%s bytes
                dataUrl=data:%s;base64,%s
                """.formatted(displayPath(path), contentType, size, contentType, base64).stripTrailing());
    }

    private byte[] readBytes(Path path, int offsetBytes, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            skipBytes(input, offsetBytes);
            return input.readNBytes(maxBytes);
        }
    }

    private void skipBytes(InputStream input, int offsetBytes) throws IOException {
        long remaining = offsetBytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                return;
            }
            remaining -= skipped;
        }
    }
}
