package com.aster.app.tool.builtin;

import com.aster.llm.model.ToolCall;
import com.aster.app.tool.builtin.model.BashToolParams;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * bash 工具：在终端执行 bash 命令。
 *
 * <p>命令工作目录固定为传入的 workingDirectory，但不会限制命令访问其他路径。
 * 输出只保留最后 2000 行 / 50KB。</p>
 */
public class BashTool extends AbstractBuiltinTool {
    private static final int MAX_OUTPUT_BYTES = 50_000;
    private static final int MAX_OUTPUT_LINES = 2_000;

    public BashTool(Path workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "bash",
                "Bash",
                "在终端执行 bash 命令，输出只保留最后 2000 行 / 50KB。",
                objectSchema(
                        Map.of(
                                "command", stringSchema("要执行的 bash 命令"),
                                "timeoutSeconds", numberSchema("超时时间，默认 30 秒，最大 120 秒")
                        ),
                        List.of("command")
                )
        );
    }

    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        BashToolParams params = BashToolParams.from(arguments);
        Process process = new ProcessBuilder("/bin/bash", "-lc", params.command())
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        CompletableFuture<LimitedOutput> output = CompletableFuture.supplyAsync(() ->
                readTailLimited(process.getInputStream(), MAX_OUTPUT_BYTES, MAX_OUTPUT_LINES)
        );

        boolean finished = process.waitFor(params.timeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolResult.error(call.id(), "命令超时，已终止: " + params.command());
        }

        LimitedOutput limitedOutput = output.get(5, TimeUnit.SECONDS);
        String text = """
                exitCode=%s
                command=%s

                %s%s
                """.formatted(
                process.exitValue(),
                params.command(),
                limitedOutput.text(),
                limitedOutput.truncated() ? "\n[输出已截断，只保留最后 2000 行 / 50KB]" : ""
        );

        return ToolResult.text(call.id(), text.stripTrailing());
    }

    private LimitedOutput readTailLimited(InputStream input, int maxBytes, int maxLines) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            boolean truncated = false;

            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > maxBytes) {
                    byte[] current = output.toByteArray();
                    output.reset();
                    output.write(current, current.length - maxBytes, maxBytes);
                    truncated = true;
                }
            }

            String text = output.toString(StandardCharsets.UTF_8);
            LimitedOutput lineLimited = keepLastLines(text, maxLines);
            return new LimitedOutput(lineLimited.text(), truncated || lineLimited.truncated());
        } catch (IOException e) {
            return new LimitedOutput("读取命令输出失败: " + e.getMessage(), false);
        }
    }

    private LimitedOutput keepLastLines(String text, int maxLines) {
        String[] lines = text.split("\\R", -1);
        if (lines.length <= maxLines) {
            return new LimitedOutput(text, false);
        }

        int start = lines.length - maxLines;
        return new LimitedOutput(String.join("\n", List.of(lines).subList(start, lines.length)), true);
    }

    /**
     * bash 输出读取结果。
     */
    private record LimitedOutput(String text, boolean truncated) {
    }
}
