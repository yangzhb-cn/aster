package com.aster.app.room;

import com.aster.app.runtime.WorkspacePaths;
import com.aster.core.session.BootstrappedSessionStore;
import com.aster.core.session.JsonlSessionStore;
import com.aster.core.session.SessionStore;
import com.aster.llm.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 创建房间 Agent 私有 session。
 */
public class RoomAgentSessionFactory {
    private final ObjectMapper objectMapper;
    private final Path sessionsDirectory;

    public RoomAgentSessionFactory(ObjectMapper objectMapper, Path sessionsDirectory) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.sessionsDirectory = Objects.requireNonNull(sessionsDirectory);
    }

    /**
     * 按 roomId 和 agentId 打开稳定的私有 JSONL session。
     */
    public SessionStore open(String roomId, String agentId, String systemPrompt) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomIdValidator.requireSafeId(agentId, "agentId");
        String sessionName = roomId + "__" + agentId;
        return new BootstrappedSessionStore(
                List.of(Message.system(systemPrompt)),
                JsonlSessionStore.openNamed(objectMapper, sessionsDirectory, sessionName)
        );
    }
}
