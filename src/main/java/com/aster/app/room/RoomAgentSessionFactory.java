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
     * 按 roomId、agentId 和成员 generation 打开私有 JSONL session。
     *
     * <p>generation 来自 RoomMembership。成员被移出聊天室后再恢复会递增 generation，
     * 从而清空该 Agent 在该聊天室中的旧私有上下文。</p>
     */
    public SessionStore open(String roomId, String agentId, int generation, String systemPrompt) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomIdValidator.requireSafeId(agentId, "agentId");
        int safeGeneration = Math.max(1, generation);
        String sessionName = roomId + "__" + agentId + "__g" + safeGeneration;
        return new BootstrappedSessionStore(
                List.of(Message.system(systemPrompt)),
                JsonlSessionStore.openNamed(objectMapper, sessionsDirectory, sessionName)
        );
    }

    /**
     * 兼容旧调用：默认使用第一代私有 session。
     */
    public SessionStore open(String roomId, String agentId, String systemPrompt) throws IOException {
        return open(roomId, agentId, 1, systemPrompt);
    }
}
