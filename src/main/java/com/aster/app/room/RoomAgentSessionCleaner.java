package com.aster.app.room;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 房间 Agent 私有 session 清理器。
 *
 * <p>物理删除房间或 Agent 时，需要同步删除对应的私有 JSONL session。</p>
 */
public class RoomAgentSessionCleaner {
    private final Path sessionsDirectory;

    public RoomAgentSessionCleaner(Path sessionsDirectory) {
        this.sessionsDirectory = Objects.requireNonNull(sessionsDirectory);
    }

    /**
     * 删除某个房间下所有 Agent 私有 session。
     */
    public void deleteRoomSessions(String roomId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        deleteMatching(roomId + "__");
    }

    /**
     * 删除某个 Agent 在所有房间中的私有 session。
     */
    public void deleteAgentSessions(String agentId) throws IOException {
        RoomIdValidator.requireSafeId(agentId, "agentId");
        deleteMatching("__" + agentId + ".jsonl");
    }

    private void deleteMatching(String token) throws IOException {
        Files.createDirectories(sessionsDirectory);
        try (var stream = Files.list(sessionsDirectory)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && name.contains(token)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }
}
