package com.aster.app.room;

import com.aster.app.runtime.WorkspacePaths;
import com.aster.app.room.model.RoomAgentProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 房间 Agent 系统提示词文件存储。
 *
 * <p>Agent 元信息保存在 agents.json，完整 system prompt 保存在 Markdown 文件，
 * 便于 Web 编辑，也方便后续手工维护。</p>
 */
public class RoomAgentPromptStore {
    private final Path directory;

    public RoomAgentPromptStore(Path directory) throws IOException {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        Files.createDirectories(directory);
    }

    /**
     * 写入指定 Agent 的系统提示词，返回可持久化的文件路径。
     */
    public synchronized String write(String agentId, String prompt) throws IOException {
        RoomIdValidator.requireSafeId(agentId, "agentId");
        Path file = fileFor(agentId);
        Files.writeString(file, cleanPrompt(prompt), StandardCharsets.UTF_8);
        Path workspaceRoot = WorkspacePaths.ROOT.toAbsolutePath().normalize();
        if (file.startsWith(workspaceRoot)) {
            return workspaceRoot.relativize(file).toString();
        }
        return file.toString();
    }

    /**
     * 读取指定 Agent 的系统提示词。
     */
    public synchronized String read(RoomAgentProfile profile) throws IOException {
        Objects.requireNonNull(profile);
        Path file = resolve(profile.systemPromptPath(), profile.agentId());
        if (!Files.exists(file)) {
            return "";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * 删除指定 Agent 的 system prompt 文件。
     */
    public synchronized void delete(RoomAgentProfile profile) throws IOException {
        Objects.requireNonNull(profile);
        Files.deleteIfExists(resolve(profile.systemPromptPath(), profile.agentId()));
    }

    private Path resolve(String rawPath, String agentId) {
        if (rawPath == null || rawPath.isBlank()) {
            return fileFor(agentId);
        }
        Path input = Path.of(rawPath);
        Path file = input.isAbsolute()
                ? input.toAbsolutePath().normalize()
                : WorkspacePaths.ROOT.toAbsolutePath().normalize().resolve(input).normalize();
        if (!file.startsWith(directory)) {
            return fileFor(agentId);
        }
        return file;
    }

    private Path fileFor(String agentId) {
        return directory.resolve(agentId + ".md").normalize();
    }

    private String cleanPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return """
                    你是一个聊天室 Agent。

                    请根据你的名称、角色、房间主题和共享消息回复用户。
                    只输出最终回复，不要描述内部工具调用过程。
                    """.strip();
        }
        return prompt.strip();
    }
}
