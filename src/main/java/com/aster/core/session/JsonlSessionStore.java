package com.aster.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.model.Message;
import com.aster.core.session.model.SessionEvent;
import com.aster.core.session.model.SessionEventType;
import com.aster.core.session.model.SessionReplayResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JSONL 版 session 存储。
 *
 * <p>它不保存“最终消息列表”，而是追加写事件。
 * 启动恢复时从 JSONL 逐行读取事件，再通过 SessionReplayer 回放成当前分支的消息列表。</p>
 */
public class JsonlSessionStore implements SessionStore {
    private final ObjectMapper objectMapper;
    private final Path file;
    private final String sessionId;
    private final String branchId;
    private final SessionReplayer replayer = new SessionReplayer();

    private long nextSeq;
    private String lastHash;
    private String activeRunId;

    public JsonlSessionStore(ObjectMapper objectMapper, Path file, String sessionId, String branchId) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.file = Objects.requireNonNull(file);
        this.sessionId = requireText(sessionId, "sessionId");
        this.branchId = requireText(branchId, "branchId");

        Files.createDirectories(file.getParent());
        initializeFromFile();
    }

    /**
     * 打开默认 main 分支。
     */
    public static JsonlSessionStore openDefault(ObjectMapper objectMapper, Path sessionsDirectory) throws IOException {
        return openNamed(objectMapper, sessionsDirectory, SessionCatalog.DEFAULT_SESSION);
    }

    /**
     * 按名称打开 main 分支；不存在时会创建新的 JSONL 文件。
     */
    public static JsonlSessionStore openNamed(ObjectMapper objectMapper, Path sessionsDirectory, String sessionName) throws IOException {
        return new JsonlSessionStore(
                objectMapper,
                SessionCatalog.fileFor(sessionsDirectory, sessionName),
                sessionName,
                SessionReplayer.MAIN_BRANCH
        );
    }

    /**
     * 追加一条 message_appended 事件。
     */
    @Override
    public synchronized void append(Message message) throws IOException {
        appendEvent(SessionEvent.draft(
                sessionId,
                branchId,
                SessionEventType.MESSAGE_APPENDED,
                activeRunId,
                null,
                null,
                message,
                null
        ));
    }

    /**
     * 回放当前分支的完整原始消息。
     */
    @Override
    public synchronized List<Message> loadMessages() throws IOException {
        return replay().messages();
    }

    /**
     * 返回更完整的回放结果，包含是否发生恢复裁剪。
     */
    public synchronized SessionReplayResult replay() throws IOException {
        return replayer.replay(readEvents(), branchId);
    }

    /**
     * 创建一个新分支。
     *
     * <p>新分支从当前 branchId 的 forkSeq 处派生。后续用同一个 JSONL 文件、
     * 但 branchId 换成 newBranchId 打开，就能看到父分支 forkSeq 之前的历史。</p>
     */
    public synchronized void createBranch(String newBranchId, long forkSeq) throws IOException {
        appendEvent(SessionEvent.draft(
                sessionId,
                requireText(newBranchId, "newBranchId"),
                SessionEventType.BRANCH_CREATED,
                activeRunId,
                branchId,
                forkSeq,
                null,
                null
        ));
    }

    /**
     * 当前已经写入的最后一个 seq。
     */
    public synchronized long currentSeq() {
        return nextSeq - 1;
    }

    @Override
    public synchronized void recordRunStarted(String userInput) throws IOException {
        activeRunId = UUID.randomUUID().toString();
        appendEvent(SessionEvent.draft(
                sessionId,
                branchId,
                SessionEventType.RUN_STARTED,
                activeRunId,
                null,
                null,
                null,
                userInput
        ));
    }

    @Override
    public synchronized void recordRunFinished(String answer) throws IOException {
        appendEvent(SessionEvent.draft(
                sessionId,
                branchId,
                SessionEventType.RUN_FINISHED,
                activeRunId,
                null,
                null,
                null,
                answer
        ));
        activeRunId = null;
    }

    @Override
    public synchronized void recordRunInterrupted(String reason) throws IOException {
        appendEvent(SessionEvent.draft(
                sessionId,
                branchId,
                SessionEventType.RUN_INTERRUPTED,
                activeRunId,
                null,
                null,
                null,
                reason
        ));
        activeRunId = null;
    }

    /**
     * 根据现有文件初始化 seq 和哈希链；空文件会写入 session_created/main branch。
     */
    private void initializeFromFile() throws IOException {
        List<SessionEvent> events = readEvents();
        if (events.isEmpty()) {
            nextSeq = 1;
            lastHash = "";
            appendEvent(SessionEvent.draft(
                    sessionId,
                    null,
                    SessionEventType.SESSION_CREATED,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            appendEvent(SessionEvent.draft(
                    sessionId,
                    SessionReplayer.MAIN_BRANCH,
                    SessionEventType.BRANCH_CREATED,
                    null,
                    null,
                    0L,
                    null,
                    null
            ));
            return;
        }

        SessionEvent last = events.getLast();
        nextSeq = last.seq() + 1;
        lastHash = last.hash() == null ? "" : last.hash();
        // 打开时做一次回放，尽早发现未知分支或分支循环。
        replayer.replay(events, branchId);
    }

    private List<SessionEvent> readEvents() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }

        List<SessionEvent> events = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                events.add(objectMapper.readValue(line, SessionEvent.class));
            }
        }
        return events;
    }

    private void appendEvent(SessionEvent draft) throws IOException {
        SessionEvent base = draft.withAuditBase(
                nextSeq,
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                lastHash
        );
        SessionEvent event = base.withHash(hash(base));
        Files.writeString(
                file,
                objectMapper.writeValueAsString(event) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        nextSeq++;
        lastHash = event.hash();
    }

    private String hash(SessionEvent event) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(objectMapper.writeValueAsBytes(event));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
