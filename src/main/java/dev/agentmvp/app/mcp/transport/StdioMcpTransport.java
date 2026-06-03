package dev.agentmvp.app.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP stdio 传输。
 *
 * <p>本地 MCP Server 通常是一个命令，例如 {@code npx ...} 或某个本地二进制。
 * Agent 启动该进程后，通过 stdin/stdout 发送 JSON-RPC。
 * 教学版先做同步请求：一次发送一个请求，等待同 id 的响应。</p>
 */
public class StdioMcpTransport implements McpTransport {
    private final ObjectMapper objectMapper;
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final Thread stderrDrainer;

    public StdioMcpTransport(
            ObjectMapper objectMapper,
            String command,
            List<String> args,
            Map<String, String> env,
            String cwd
    ) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        Objects.requireNonNull(command);

        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        if (args != null) {
            commandLine.addAll(args);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        if (env != null && !env.isEmpty()) {
            processBuilder.environment().putAll(env);
        }
        if (cwd != null && !cwd.isBlank()) {
            processBuilder.directory(Path.of(cwd).toFile());
        }

        this.process = processBuilder.start();
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stderrDrainer = startStderrDrainer(process);
    }

    /**
     * 发送一行 JSON-RPC，并等待同 id 的响应。
     */
    @Override
    public synchronized String send(String requestJson) throws IOException {
        JsonNode request = objectMapper.readTree(requestJson);
        JsonNode expectedId = request.path("id");

        stdin.write(requestJson);
        stdin.newLine();
        stdin.flush();

        while (true) {
            String line = stdout.readLine();
            if (line == null) {
                throw new IOException("MCP stdio server exited before response");
            }

            JsonNode response = objectMapper.readTree(line);
            if (response.has("id") && response.path("id").equals(expectedId)) {
                return line;
            }
            // MCP server 可能主动发 notification。教学版不处理通知，只跳过。
        }
    }

    /**
     * 关闭本地 MCP 进程。
     */
    @Override
    public void close() throws IOException {
        try {
            stdin.close();
            stdout.close();
        } finally {
            process.destroy();
            stderrDrainer.interrupt();
        }
    }

    private Thread startStderrDrainer(Process process) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
            )) {
                while (!Thread.currentThread().isInterrupted() && reader.readLine() != null) {
                    // 教学版只负责避免 stderr 阻塞进程；暂不把日志接入 TUI。
                }
            } catch (IOException ignored) {
                // 进程关闭时这里可能抛异常，不影响主流程。
            }
        }, "mcp-stdio-stderr-drainer");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
