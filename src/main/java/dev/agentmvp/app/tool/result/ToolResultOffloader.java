package dev.agentmvp.app.tool.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.core.tool.model.ToolResult;
import dev.agentmvp.app.tool.result.model.StoredToolResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 工具结果卸载器。
 *
 * <p>AgentLoop 必须把工具结果写成 {@code role=tool} 消息，但不一定要把完整大文本
 * 都塞进上下文。这个类负责在写入 session 前判断结果是否过大：
 * 小结果原样内联，大结果写入 workspace/artifacts/tool-results/*.jsonl，
 * 然后只把预览、绝对路径和 recordId 留在 tool 消息里。</p>
 */
public class ToolResultOffloader {
    private static final int DEFAULT_MAX_INLINE_CHARS = 50_000;
    private static final int DEFAULT_PREVIEW_CHARS = 4_000;

    private final ObjectMapper objectMapper;
    private final Path outputDirectory;
    private final int maxInlineChars;
    private final int previewChars;

    public ToolResultOffloader(
            ObjectMapper objectMapper,
            Path outputDirectory,
            int maxInlineChars,
            int previewChars
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.outputDirectory = outputDirectory == null ? null : outputDirectory.toAbsolutePath();
        this.maxInlineChars = maxInlineChars;
        this.previewChars = previewChars;
    }

    /**
     * 创建默认卸载器。
     */
    public static ToolResultOffloader defaults(ObjectMapper objectMapper, Path outputDirectory) {
        return new ToolResultOffloader(
                objectMapper,
                outputDirectory,
                DEFAULT_MAX_INLINE_CHARS,
                DEFAULT_PREVIEW_CHARS
        );
    }

    /**
     * 创建只内联、不落盘的卸载器，方便测试和不需要 artifact 的入口复用旧行为。
     */
    public static ToolResultOffloader inlineOnly() {
        return new ToolResultOffloader(new ObjectMapper(), null, Integer.MAX_VALUE, DEFAULT_PREVIEW_CHARS);
    }

    /**
     * 把工具结果转成最终写入 role=tool 的文本。
     */
    public String compact(ToolResult result) throws IOException {
        String renderedText = result.renderText();
        if (renderedText.length() <= maxInlineChars || outputDirectory == null) {
            return renderedText;
        }

        StoredFile storedFile = store(result, renderedText);
        return renderCompactMessage(result, renderedText, storedFile);
    }

    private StoredFile store(ToolResult result, String renderedText) throws IOException {
        Files.createDirectories(outputDirectory);

        String recordId = UUID.randomUUID().toString();
        Path path = outputDirectory.resolve(recordId + ".jsonl").toAbsolutePath();
        StoredToolResult stored = new StoredToolResult(
                recordId,
                Instant.now().toString(),
                result.toolCallId(),
                result.toolName(),
                result.error(),
                result.elapsedMillis(),
                renderedText.length(),
                renderedText
        );

        String line = objectMapper.writeValueAsString(stored) + "\n";
        Files.writeString(path, line, StandardCharsets.UTF_8);
        return new StoredFile(recordId, path);
    }

    private String renderCompactMessage(ToolResult result, String renderedText, StoredFile storedFile) {
        String preview = head(renderedText, previewChars);
        return """
                工具结果过大，完整结果已卸载到 JSONL 文件。

                tool=%s
                toolCallId=%s
                recordId=%s
                jsonlPath=%s
                fullChars=%s
                inlinePreviewChars=%s

                可使用 read 工具读取 jsonlPath 查看完整结果。

                inlinePreview:
                %s
                """.formatted(
                nullToUnknown(result.toolName()),
                result.toolCallId(),
                storedFile.recordId(),
                storedFile.path(),
                renderedText.length(),
                preview.length(),
                preview
        ).stripTrailing();
    }

    private String head(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars)) + "\n...[preview truncated]";
    }

    private String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private record StoredFile(String recordId, Path path) {
    }
}
