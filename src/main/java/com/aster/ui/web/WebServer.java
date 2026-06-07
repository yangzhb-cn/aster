package com.aster.ui.web;

import com.aster.app.runtime.AgentRuntime;
import com.aster.app.runtime.AgentRuntimeFactory;
import com.aster.app.runtime.JsonContextWindowSnapshotStore;
import com.aster.app.runtime.WorkspacePaths;
import com.aster.app.hitl.model.ToolApprovalRequest;
import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentInput;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomMemberView;
import com.aster.app.room.model.RoomSendResult;
import com.aster.app.schedule.model.ScheduledUserMessage;
import com.aster.app.skill.model.SkillMetadata;
import com.aster.app.team.model.TeamRunRequest;
import com.aster.app.todo.JsonTodoStore;
import com.aster.app.todo.TodoStore;
import com.aster.app.todo.model.TodoItem;
import com.aster.core.session.JsonlSessionStore;
import com.aster.core.session.SessionCatalog;
import com.aster.core.session.SessionIndex;
import com.aster.core.session.model.SessionRecord;
import com.aster.llm.model.Message;
import com.aster.llm.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Aster Web HTTP 服务。
 *
 * <p>它提供静态页面、消息提交接口和 SSE 事件接口。
 * 业务执行全部委托给 AgentRuntime，Web 层不直接接触 AgentLoop。</p>
 */
public class WebServer implements AutoCloseable {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSseClientRegistry clients = new WebSseClientRegistry();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final WebAgentEventHandler eventHandler;
    private final WebNotificationSink notificationSink;
    private final WebSessionRuntimePool runtimePool;
    private final SessionIndex sessionIndex;
    private final JsonContextWindowSnapshotStore contextWindowSnapshotStore;
    private final TodoStore todoStore;
    private final Object runtimeLock = new Object();
    private final HttpServer server;
    private String currentSessionId;
    private String currentDisplayName;

    public WebServer(int port, AgentRuntimeFactory runtimeFactory, String sessionName) throws IOException {
        this.eventHandler = new WebAgentEventHandler(objectMapper, clients);
        this.notificationSink = new WebNotificationSink(objectMapper, clients);
        this.runtimePool = new WebSessionRuntimePool(Objects.requireNonNull(runtimeFactory), eventHandler, notificationSink);
        this.sessionIndex = new SessionIndex(objectMapper, WorkspacePaths.SESSIONS);
        this.contextWindowSnapshotStore = new JsonContextWindowSnapshotStore(objectMapper, WorkspacePaths.CONTEXT_WINDOWS);
        this.todoStore = new JsonTodoStore(objectMapper, WorkspacePaths.TODO_FILE);
        SessionRecord session = initialSession(sessionName);
        if (session == null) {
            clearCurrentSession();
        } else {
            this.currentSessionId = session.id();
            this.currentDisplayName = session.displayName();
            this.runtimePool.runtimeFor(session.id());
        }
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(executor);
        registerRoutes();
    }

    /**
     * 启动 Web 服务。
     */
    public void start() {
        server.start();
        heartbeat.scheduleAtFixedRate(
                () -> clients.broadcast("ping", "{\"status\":\"alive\"}"),
                5,
                5,
                TimeUnit.SECONDS
        );
    }

    /**
     * 当前监听端口。
     */
    public int port() {
        return server.getAddress().getPort();
    }

    private void registerRoutes() {
        server.createContext("/", this::handleStatic);
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/api/messages", this::handleMessageSubmit);
        server.createContext("/api/steer", this::handleSteer);
        server.createContext("/api/stop", this::handleStop);
        server.createContext("/api/model", this::handleModel);
        server.createContext("/api/approvals", this::handleApprovals);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/todos", this::handleTodos);
        server.createContext("/api/schedules", this::handleSchedules);
        server.createContext("/api/rooms", this::handleRooms);
        server.createContext("/api/room-agents", this::handleRoomAgents);
        server.createContext("/api/archives", this::handleArchives);
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        WebSseClientRegistry.Client client = clients.add(exchange.getResponseBody());
        try {
            client.awaitClosed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            client.close();
        } finally {
            exchange.close();
        }
    }

    private void handleMessageSubmit(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            Map<?, ?> payload = readPayload(exchange);
            String text = stringValue(payload, "text");
            if (text.isBlank()) {
                sendJson(exchange, 400, Map.of("error", "text is required"));
                return;
            }

            String sessionId = sessionIdFromPayload(payload);
            synchronized (runtimeLock) {
                SessionRecord session = sessionId.isBlank()
                        ? createSessionForFirstMessage(text)
                        : activeSession(sessionId);
                selectSession(session);
                AgentRuntime target = runtimePool.runtimeFor(session.id());
                if ("/start".equals(text)) {
                    if (!target.startPlan()) {
                        sendJson(exchange, 409, Map.of("error", "当前没有待执行的 Plan"));
                        return;
                    }
                } else if (text.startsWith("/plan")) {
                    String task = text.length() <= "/plan".length() ? "" : text.substring("/plan".length()).trim();
                    if ("cancel".equalsIgnoreCase(task)) {
                        if (!target.cancelPlan()) {
                            sendJson(exchange, 409, Map.of("error", "当前没有可取消的 Plan"));
                            return;
                        }
                    } else if (task.isBlank()) {
                        sendJson(exchange, 400, Map.of("error", "用法：/plan 要完成的任务"));
                        return;
                    } else {
                        target.submitPlan(task);
                    }
                } else if (text.startsWith("/team")) {
                    String raw = text.length() <= "/team".length() ? "" : text.substring("/team".length()).trim();
                    TeamRunRequest request = TeamRunRequest.parse(raw);
                    if (request.task().isBlank()) {
                        sendJson(exchange, 400, Map.of("error", "用法：/team [--model 模型名] 要探索的问题"));
                        return;
                    }
                    target.submitTeam(request.task(), request.model());
                } else if (text.equals("/model") || text.startsWith("/model ")) {
                    String model = text.length() <= "/model".length() ? "" : text.substring("/model".length()).trim();
                    if (!model.isBlank()) {
                        target.switchChatModel(model);
                    }
                } else {
                    target.submit(text);
                }
                sessionIndex.touch(session.id());
            }
            sendJson(exchange, 202, statusPayload());
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            sendJson(exchange, 409, Map.of("error", e.getMessage()));
        } catch (IOException e) {
            sendJson(exchange, sessionErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    private void handleSteer(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            Map<?, ?> payload = readPayload(exchange);
            String text = stringValue(payload, "text");
            if (text.isBlank()) {
                sendJson(exchange, 400, Map.of("error", "text is required"));
                return;
            }

            boolean accepted;
            synchronized (runtimeLock) {
                String sessionId = sessionIdFromPayload(payload);
                accepted = runtimeForActiveSession(sessionId).steer(text);
            }
            sendJson(exchange, accepted ? 202 : 409, statusPayload());
        } catch (IllegalArgumentException | DateTimeException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (IOException e) {
            sendJson(exchange, sessionErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    private void handleStop(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            Map<?, ?> payload = readPayload(exchange);
            boolean accepted;
            synchronized (runtimeLock) {
                String sessionId = sessionIdFromPayload(payload);
                accepted = runtimeForActiveSession(sessionId).stop();
            }
            sendJson(exchange, accepted ? 202 : 409, statusPayload());
        } catch (IOException e) {
            sendJson(exchange, sessionErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    /**
     * 切换当前 Web session 的 Chat 模型。
     */
    private void handleModel(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            Map<?, ?> payload = readPayload(exchange);
            String model = requiredValue(payload, "model");
            synchronized (runtimeLock) {
                String sessionId = sessionIdFromPayload(payload);
                runtimeForActiveSession(sessionId).switchChatModel(model);
            }
            sendJson(exchange, 200, statusPayload());
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (IOException e) {
            sendJson(exchange, sessionErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }
        sendJson(exchange, 200, statusPayload());
    }

    /**
     * 处理 Web 工具审批。
     */
    private void handleApprovals(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            String path = exchange.getRequestURI().getPath();
            Map<?, ?> payload = readPayload(exchange);
            String approvalId = stringValue(payload, "id");
            boolean accepted;
            int count = 0;
            synchronized (runtimeLock) {
                AgentRuntime target = runtimeForActiveSession(sessionIdFromPayload(payload));
                if ("/api/approvals/approve".equals(path)) {
                    if (approvalId.isBlank()) {
                        count = target.approveAllTools();
                        accepted = count > 0;
                    } else {
                        accepted = target.approveTool(approvalId);
                        count = accepted ? 1 : 0;
                    }
                } else if ("/api/approvals/deny".equals(path)) {
                    String reason = stringValue(payload, "reason");
                    if (approvalId.isBlank()) {
                        count = target.denyAllTools(reason.isBlank() ? "用户拒绝全部待审批工具" : reason);
                        accepted = count > 0;
                    } else {
                        accepted = target.denyTool(approvalId, reason);
                        count = accepted ? 1 : 0;
                    }
                } else {
                    sendJson(exchange, 404, Map.of("error", "Not Found"));
                    return;
                }
            }
            sendJson(exchange, accepted ? 202 : 404, Map.of(
                    "accepted", accepted,
                    "count", count,
                    "status", statusPayload()
            ));
        } catch (IOException e) {
            sendJson(exchange, sessionErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    /**
     * 处理 Web 会话 CRUD。
     *
     * <p>这里只管理 session 索引和当前选中 session；真实消息仍由 JsonlSessionStore 读写。</p>
     */
    private void handleSessions(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String messageSessionId = messageSessionId(path);
            if (messageSessionId != null) {
                handleSessionMessages(exchange, messageSessionId);
                return;
            }

            if ("/api/sessions".equals(path)) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, sessionsPayload());
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    createSession(exchange);
                    return;
                }
            }

            if ("/api/sessions/use".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                useSession(exchange);
                return;
            }
            if ("/api/sessions/rename".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                renameSession(exchange);
                return;
            }
            if ("/api/sessions/archive".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                archiveSession(exchange);
                return;
            }

            sendJson(exchange, 404, Map.of("error", "Not Found"));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (IOException e) {
            sendJson(exchange, sessionErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    /**
     * 处理 Web 便签待办 CRUD。
     */
    private void handleTodos(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/api/todos".equals(path)) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, todosPayload());
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    createTodo(exchange);
                    return;
                }
            }
            if ("/api/todos/update".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                updateTodo(exchange);
                return;
            }
            if ("/api/todos/complete".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                completeTodo(exchange);
                return;
            }
            if ("/api/todos/archive".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                archiveTodo(exchange);
                return;
            }
            sendJson(exchange, 404, Map.of("error", "Not Found"));
        } catch (IOException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    /**
     * 处理 Web 自动化用户消息 CRUD。
     */
    private void handleSchedules(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/api/schedules".equals(path)) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, schedulesPayload());
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    createSchedule(exchange);
                    return;
                }
            }
            if ("/api/schedules/cancel".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                cancelSchedule(exchange);
                return;
            }
            sendJson(exchange, 404, Map.of("error", "Not Found"));
        } catch (IOException e) {
            sendJson(exchange, sessionErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    /**
     * 处理 Web 聊天室 CRUD 和房间消息。
     */
    private void handleRooms(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String messageRoomId = roomMessageId(path);
            if (messageRoomId != null) {
                handleRoomMessages(exchange, messageRoomId);
                return;
            }
            RoomMemberRoute memberRoute = roomMemberRoute(path);
            if (memberRoute != null) {
                handleRoomMembers(exchange, memberRoute);
                return;
            }

            if ("/api/rooms".equals(path)) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, roomsPayload());
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    createRoom(exchange);
                    return;
                }
            }
            if ("/api/rooms/update".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                updateRoom(exchange);
                return;
            }
            if ("/api/rooms/archive".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                archiveRoom(exchange);
                return;
            }
            sendJson(exchange, 404, Map.of("error", "Not Found"));
        } catch (IOException e) {
            sendJson(exchange, roomErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    /**
     * 处理房间 Agent CRUD。
     */
    private void handleRoomAgents(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/api/room-agents".equals(path)) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, roomAgentsPayload());
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    createRoomAgent(exchange);
                    return;
                }
            }
            if ("/api/room-agents/update".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                updateRoomAgent(exchange);
                return;
            }
            if ("/api/room-agents/archive".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                archiveRoomAgent(exchange);
                return;
            }
            sendJson(exchange, 404, Map.of("error", "Not Found"));
        } catch (IOException e) {
            sendJson(exchange, roomErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    /**
     * 处理归档中心。
     */
    private void handleArchives(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/api/archives".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, archivesPayload());
                return;
            }
            if ("/api/archives/restore".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                restoreArchive(exchange);
                return;
            }
            if ("/api/archives/delete".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                deleteArchive(exchange);
                return;
            }
            if ("/api/archives/delete-batch".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                deleteArchiveBatch(exchange);
                return;
            }
            sendJson(exchange, 404, Map.of("error", "Not Found"));
        } catch (IOException e) {
            sendJson(exchange, archiveErrorStatus(e), Map.of("error", e.getMessage()));
        }
    }

    private void createTodo(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        todoStore.add(
                requiredValue(payload, "content"),
                stringValue(payload, "priority"),
                stringValue(payload, "dueAt")
        );
        sendJson(exchange, 201, todosPayload());
    }

    private void updateTodo(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        todoStore.update(
                requiredValue(payload, "id"),
                stringValue(payload, "content"),
                stringValue(payload, "priority"),
                stringValue(payload, "dueAt")
        );
        sendJson(exchange, 200, todosPayload());
    }

    private void completeTodo(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        todoStore.complete(requiredValue(payload, "id"), stringValue(payload, "result"));
        sendJson(exchange, 200, todosPayload());
    }

    private void archiveTodo(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        todoStore.archive(requiredValue(payload, "id"));
        sendJson(exchange, 200, todosPayload());
    }

    private void createSchedule(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        synchronized (runtimeLock) {
            AgentRuntime runtime = currentRuntime();
            String type = requiredValue(payload, "type");
            String name = stringValue(payload, "name");
            String content = requiredValue(payload, "content");
            switch (type) {
                case "daily" -> runtime.createDailySchedule(
                        name,
                        content,
                        requiredValue(payload, "dailyTime"),
                        stringValue(payload, "timezone")
                );
                case "once" -> runtime.createOnceSchedule(
                        name,
                        content,
                        requiredValue(payload, "runAt")
                );
                case "interval" -> runtime.createIntervalSchedule(
                        name,
                        content,
                        longValue(payload, "intervalSeconds")
                );
                default -> throw new IOException("unknown schedule type: " + type);
            }
        }
        sendJson(exchange, 201, schedulesPayload());
    }

    private void cancelSchedule(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        synchronized (runtimeLock) {
            currentRuntime().cancelSchedule(requiredValue(payload, "id"));
        }
        sendJson(exchange, 200, schedulesPayload());
    }

    private void createRoom(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        synchronized (runtimeLock) {
            currentRuntime().createRoom(stringValue(payload, "name"));
        }
        sendJson(exchange, 201, roomsPayload());
    }

    private void updateRoom(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        synchronized (runtimeLock) {
            currentRuntime().updateRoom(
                    requiredValue(payload, "roomId"),
                    stringValue(payload, "name"),
                    stringValue(payload, "topic")
            );
        }
        sendJson(exchange, 200, roomsPayload());
    }

    private void archiveRoom(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        synchronized (runtimeLock) {
            currentRuntime().archiveRoom(requiredValue(payload, "roomId"));
        }
        sendJson(exchange, 200, roomsPayload());
    }

    private void handleRoomMessages(HttpExchange exchange, String roomId) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            synchronized (runtimeLock) {
                sendJson(exchange, 200, Map.of("messages", currentRuntime().roomMessages(roomId).stream()
                        .map(this::hubMessagePayload)
                        .toList()));
            }
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            String text = readTextPayload(exchange);
            RoomSendResult result;
            synchronized (runtimeLock) {
                result = currentRuntime().sendRoomMessage(roomId, text);
            }
            sendJson(exchange, 202, Map.of(
                    "room", roomPayload(result.room()),
                    "messages", result.messages().stream().map(this::hubMessagePayload).toList()
            ));
            return;
        }
        sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
    }

    private void handleRoomMembers(HttpExchange exchange, RoomMemberRoute route) throws IOException {
        if ("GET".equals(exchange.getRequestMethod()) && route.action().isBlank()) {
            sendJson(exchange, 200, roomMembersPayload(route.roomId()));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        Map<?, ?> payload = readPayload(exchange);
        synchronized (runtimeLock) {
            switch (route.action()) {
                case "" -> currentRuntime().addRoomMember(route.roomId(), requiredValue(payload, "agentId"));
                case "archive" -> currentRuntime().archiveRoomMember(route.roomId(), requiredValue(payload, "agentId"));
                case "restore" -> currentRuntime().restoreRoomMember(route.roomId(), requiredValue(payload, "agentId"));
                default -> {
                    sendJson(exchange, 404, Map.of("error", "Not Found"));
                    return;
                }
            }
        }
        sendJson(exchange, 200, roomMembersPayload(route.roomId()));
    }

    private void createRoomAgent(HttpExchange exchange) throws IOException {
        RoomAgentInput input = objectMapper.convertValue(readPayload(exchange), RoomAgentInput.class);
        synchronized (runtimeLock) {
            currentRuntime().createRoomAgent(input);
        }
        sendJson(exchange, 201, roomAgentsPayload());
    }

    private void updateRoomAgent(HttpExchange exchange) throws IOException {
        RoomAgentInput input = objectMapper.convertValue(readPayload(exchange), RoomAgentInput.class);
        synchronized (runtimeLock) {
            currentRuntime().updateRoomAgent(input);
        }
        sendJson(exchange, 200, roomAgentsPayload());
    }

    private void archiveRoomAgent(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        synchronized (runtimeLock) {
            currentRuntime().archiveRoomAgent(requiredValue(payload, "agentId"));
        }
        sendJson(exchange, 200, roomAgentsPayload());
    }

    private void restoreArchive(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        String type = requiredValue(payload, "type");
        String id = requiredValue(payload, "id");
        synchronized (runtimeLock) {
            switch (type) {
                case "session" -> sessionIndex.restore(id);
                case "todo" -> todoStore.restore(id);
                case "room" -> currentRuntime().restoreRoom(id);
                case "room-agent" -> currentRuntime().restoreRoomAgent(id);
                default -> throw new IOException("unknown archive type: " + type);
            }
        }
        sendJson(exchange, 200, archivesPayload());
    }

    private void deleteArchive(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        String type = requiredValue(payload, "type");
        String id = requiredValue(payload, "id");
        synchronized (runtimeLock) {
            deleteArchiveItem(type, id);
        }
        sendJson(exchange, 200, archivesPayload());
    }

    private void deleteArchiveBatch(HttpExchange exchange) throws IOException {
        List<ArchiveDeleteItem> items = archiveDeleteItems(readPayload(exchange));
        synchronized (runtimeLock) {
            for (ArchiveDeleteItem item : items) {
                deleteArchiveItem(item.type(), item.id());
            }
        }
        sendJson(exchange, 200, archivesPayload());
    }

    private void deleteArchiveItem(String type, String id) throws IOException {
        switch (type) {
            case "session" -> {
                runtimePool.closeIfIdle(id);
                sessionIndex.deletePermanently(id);
                contextWindowSnapshotStore.deleteSession(id);
            }
            case "todo" -> todoStore.deletePermanently(id);
            case "room" -> currentRuntime().deleteRoomPermanently(id);
            case "room-agent" -> currentRuntime().deleteRoomAgentPermanently(id);
            default -> throw new IOException("unknown archive type: " + type);
        }
    }

    private void createSession(HttpExchange exchange) throws IOException {
        String displayName = readStringPayload(exchange, "displayName");
        SessionRecord session = sessionIndex.create(displayName);
        selectSession(session);
        sendJson(exchange, 201, sessionsPayload());
    }

    private void useSession(HttpExchange exchange) throws IOException {
        String sessionId = readRequiredPayload(exchange, "id");
        SessionRecord session = activeSession(sessionId);
        selectSession(session);
        sendJson(exchange, 200, sessionsPayload());
    }

    private void renameSession(HttpExchange exchange) throws IOException {
        Map<?, ?> payload = readPayload(exchange);
        String sessionId = requiredValue(payload, "id");
        String displayName = requiredValue(payload, "displayName");
        SessionRecord session = sessionIndex.rename(sessionId, displayName);
        synchronized (runtimeLock) {
            if (session.id().equals(currentSessionId)) {
                currentDisplayName = session.displayName();
            }
        }
        sendJson(exchange, 200, sessionsPayload());
    }

    private void archiveSession(HttpExchange exchange) throws IOException {
        String sessionId = readRequiredPayload(exchange, "id");
        synchronized (runtimeLock) {
            activeSession(sessionId);
            runtimePool.closeIfIdle(sessionId);
            sessionIndex.archive(sessionId);

            if (sessionId.equals(currentSessionId)) {
                List<SessionRecord> sessions = sessionIndex.listActive();
                if (sessions.isEmpty()) {
                    clearCurrentSession();
                } else {
                    selectSession(sessions.getFirst());
                }
            }
        }

        sendJson(exchange, 200, sessionsPayload());
    }

    private void handleSessionMessages(HttpExchange exchange, String sessionId) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        activeSession(sessionId);
        List<Map<String, Object>> messages;
        if (!SessionCatalog.exists(WorkspacePaths.SESSIONS, sessionId)) {
            messages = List.of();
        } else {
            messages = JsonlSessionStore.openNamed(objectMapper, WorkspacePaths.SESSIONS, sessionId)
                    .loadMessages()
                    .stream()
                    .map(this::messagePayload)
                    .toList();
        }
        sendJson(exchange, 200, Map.of("messages", messages));
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/")) {
            path = "/web/index.html";
        } else if (path.startsWith("/assets/")) {
            path = "/web" + path;
        } else {
            sendText(exchange, 404, "Not Found", "text/plain; charset=utf-8");
            return;
        }

        try (InputStream input = WebServer.class.getResourceAsStream(path)) {
            if (input == null) {
                sendText(exchange, 404, "Not Found", "text/plain; charset=utf-8");
                return;
            }
            byte[] bytes = input.readAllBytes();
            sendBytes(exchange, 200, bytes, contentType(path));
        }
    }

    private String readTextPayload(HttpExchange exchange) throws IOException {
        return readStringPayload(exchange, "text");
    }

    private String readRequiredPayload(HttpExchange exchange, String key) throws IOException {
        String value = readStringPayload(exchange, key);
        if (value.isBlank()) {
            throw new IOException(key + " is required");
        }
        return value;
    }

    private String readStringPayload(HttpExchange exchange, String key) throws IOException {
        return stringValue(readPayload(exchange), key);
    }

    private Map<?, ?> readPayload(HttpExchange exchange) throws IOException {
        return objectMapper.readValue(exchange.getRequestBody(), Map.class);
    }

    private String requiredValue(Map<?, ?> payload, String key) throws IOException {
        String value = stringValue(payload, key);
        if (value.isBlank()) {
            throw new IOException(key + " is required");
        }
        return value;
    }

    private String stringValue(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private long longValue(Map<?, ?> payload, String key) throws IOException {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = stringValue(payload, key);
        if (text.isBlank()) {
            throw new IOException(key + " is required");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IOException(key + " must be a number", e);
        }
    }

    private List<ArchiveDeleteItem> archiveDeleteItems(Map<?, ?> payload) throws IOException {
        String key = "items";
        Object value = payload.get(key);
        if (!(value instanceof List<?> rawList)) {
            throw new IOException(key + " is required");
        }
        List<ArchiveDeleteItem> result = new java.util.ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                throw new IOException(key + " must contain objects");
            }
            String type = requiredValue(itemMap, "type");
            String id = requiredValue(itemMap, "id");
            result.add(new ArchiveDeleteItem(type, id));
        }
        if (result.isEmpty()) {
            throw new IOException(key + " is required");
        }
        return result;
    }

    private Map<String, Object> statusPayload() throws IOException {
        Map<String, Object> status = new LinkedHashMap<>();
        synchronized (runtimeLock) {
            status.put("currentSessionId", currentSessionId);
            status.put("sessionId", currentSessionId);
            status.put("sessionName", currentSessionId);
            status.put("displayName", currentDisplayName);
            if (hasCurrentSession()) {
                AgentRuntime selected = currentRuntime();
                status.put("model", selected.chatModel());
                status.put("availableModels", selected.availableChatModels());
                status.put("provider", selected.provider().name());
                status.put("skillCount", selected.skillCount());
                status.put("skills", selected.skillMetadata().stream()
                        .map(this::skillPayload)
                        .toList());
                status.put("mcpServers", selected.mcpServerStatuses().stream()
                        .map(this::mcpServerPayload)
                        .toList());
                status.put("busy", selected.isBusy());
                status.put("pendingPlan", selected.hasPendingPlan());
                status.put("queuedCount", selected.queuedCount());
                status.put("pendingApprovals", selected.pendingToolApprovals().stream()
                        .map(this::approvalPayload)
                        .toList());
            } else {
                status.put("model", "");
                status.put("availableModels", List.of());
                status.put("provider", "");
                status.put("skillCount", 0);
                status.put("skills", List.of());
                status.put("mcpServers", List.of());
                status.put("busy", false);
                status.put("pendingPlan", false);
                status.put("queuedCount", 0);
                status.put("pendingApprovals", List.of());
            }
            status.put("sessionStatuses", sessionStatusesPayload());
        }
        status.put("sseClients", clients.size());
        return status;
    }

    private Map<String, Object> mcpServerPayload(com.aster.app.mcp.McpToolExecutor.McpServerStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverId", status.serverId());
        payload.put("loaded", status.loaded());
        payload.put("toolCount", status.toolCount());
        payload.put("errorMessage", status.errorMessage());
        return payload;
    }

    private Map<String, Object> skillPayload(SkillMetadata skill) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", skill.name());
        payload.put("description", skill.description());
        return payload;
    }

    private Map<String, Object> sessionsPayload() throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessions", sessionIndex.listActive());
        payload.put("currentSessionId", currentSessionId);
        payload.put("status", statusPayload());
        return payload;
    }

    private Map<String, Object> sessionStatusesPayload() throws IOException {
        List<String> sessionIds = sessionIndex.listActive().stream()
                .map(SessionRecord::id)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        for (WebSessionRuntimeStatus status : runtimePool.statuses(sessionIds).values()) {
            payload.put(status.sessionId(), sessionStatusPayload(status));
        }
        return payload;
    }

    private Map<String, Object> sessionStatusPayload(WebSessionRuntimeStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", status.sessionId());
        payload.put("active", status.active());
        payload.put("busy", status.busy());
        payload.put("pendingPlan", status.pendingPlan());
        payload.put("queuedCount", status.queuedCount());
        payload.put("pendingApprovalCount", status.pendingApprovalCount());
        return payload;
    }

    private Map<String, Object> todosPayload() throws IOException {
        return Map.of("todos", todoStore.listActive().stream()
                .map(this::todoPayload)
                .toList());
    }

    private Map<String, Object> schedulesPayload() throws IOException {
        synchronized (runtimeLock) {
            if (!hasCurrentSession()) {
                return Map.of("schedules", List.of());
            }
            return Map.of("schedules", currentRuntime().listSchedules().stream()
                    .map(this::schedulePayload)
                    .toList());
        }
    }

    private Map<String, Object> archivesPayload() throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        synchronized (runtimeLock) {
            payload.put("sessions", sessionIndex.listArchived().stream()
                    .map(this::sessionPayload)
                    .toList());
            payload.put("todos", todoStore.listArchived().stream()
                    .map(this::todoPayload)
                    .toList());
            if (hasCurrentSession()) {
                payload.put("rooms", currentRuntime().listArchivedRooms().stream()
                        .map(this::roomPayload)
                        .toList());
                payload.put("roomAgents", currentRuntime().listArchivedRoomAgents().stream()
                        .map(this::roomAgentPayload)
                        .toList());
            } else {
                payload.put("rooms", List.of());
                payload.put("roomAgents", List.of());
            }
        }
        return payload;
    }

    private Map<String, Object> roomsPayload() throws IOException {
        synchronized (runtimeLock) {
            if (!hasCurrentSession()) {
                return Map.of("rooms", List.of());
            }
            AgentRuntime selected = currentRuntime();
            return Map.of("rooms", selected.listRooms().stream()
                    .map(this::roomPayload)
                    .toList());
        }
    }

    private Map<String, Object> roomAgentsPayload() throws IOException {
        synchronized (runtimeLock) {
            if (!hasCurrentSession()) {
                return Map.of("agents", List.of());
            }
            return Map.of("agents", currentRuntime().listRoomAgents().stream()
                    .map(this::roomAgentPayload)
                    .toList());
        }
    }

    private Map<String, Object> roomMembersPayload(String roomId) throws IOException {
        synchronized (runtimeLock) {
            AgentRuntime selected = currentRuntime();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("members", selected.listRoomMembers(roomId).stream()
                    .map(this::roomMemberPayload)
                    .toList());
            payload.put("removed", selected.listArchivedRoomMembers(roomId).stream()
                    .map(this::roomMemberPayload)
                    .toList());
            payload.put("availableAgents", selected.listAvailableRoomAgents(roomId).stream()
                    .map(this::roomAgentPayload)
                    .toList());
            return payload;
        }
    }

    private Map<String, Object> roomPayload(ChatRoom room) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", room.roomId());
        payload.put("name", room.name());
        payload.put("topic", room.topic());
        payload.put("createdAt", room.createdAt());
        payload.put("updatedAt", room.updatedAt());
        payload.put("archived", room.archived());
        return payload;
    }

    private Map<String, Object> hubMessagePayload(HubMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", message.roomId());
        payload.put("messageId", message.messageId());
        payload.put("runId", message.runId());
        payload.put("parentMessageId", message.parentMessageId());
        payload.put("speakerType", message.speakerType().name());
        payload.put("speakerId", message.speakerId());
        payload.put("speakerName", message.speakerName());
        payload.put("speakerRole", message.speakerRole());
        payload.put("type", message.type().name());
        payload.put("content", message.content());
        payload.put("mentions", message.mentions());
        payload.put("replyIndex", message.replyIndex());
        payload.put("createdAt", message.createdAt());
        return payload;
    }

    private Map<String, Object> roomMemberPayload(RoomMemberView view) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", view.membership().roomId());
        payload.put("agentId", view.membership().agentId());
        payload.put("orderIndex", view.membership().orderIndex());
        payload.put("generation", view.membership().generation());
        payload.put("archived", view.membership().archived());
        payload.put("createdAt", view.membership().createdAt());
        payload.put("updatedAt", view.membership().updatedAt());
        payload.put("agent", roomAgentPayload(view.agent()));
        return payload;
    }

    private Map<String, Object> roomAgentPayload(RoomAgentProfile agent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agent.agentId());
        payload.put("name", agent.name());
        payload.put("role", agent.role());
        payload.put("description", agent.description());
        payload.put("systemPromptPath", agent.systemPromptPath());
        payload.put("mentionAliases", agent.mentionAliases());
        payload.put("toolAllowlist", agent.toolAllowlist());
        payload.put("model", agent.model());
        payload.put("enabled", agent.enabled());
        payload.put("archived", agent.archived());
        payload.put("createdAt", agent.createdAt());
        payload.put("updatedAt", agent.updatedAt());
        try {
            payload.put("systemPrompt", currentRuntime().roomAgentPrompt(agent));
        } catch (IOException e) {
            payload.put("systemPrompt", "");
        }
        return payload;
    }

    private Map<String, Object> todoPayload(TodoItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.id());
        payload.put("content", item.content());
        payload.put("status", item.status().name());
        payload.put("priority", item.priority());
        payload.put("dueAt", item.dueAt());
        payload.put("result", item.result());
        payload.put("createdAt", item.createdAt());
        payload.put("updatedAt", item.updatedAt());
        return payload;
    }

    private Map<String, Object> schedulePayload(ScheduledUserMessage schedule) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", schedule.id());
        payload.put("sessionId", schedule.sessionId());
        payload.put("name", schedule.name());
        payload.put("content", schedule.content());
        payload.put("enabled", schedule.enabled());
        payload.put("status", schedule.status().name());
        payload.put("trigger", schedule.trigger());
        payload.put("nextRunAt", schedule.nextRunAt());
        payload.put("lastRunAt", schedule.lastRunAt());
        payload.put("createdAt", schedule.createdAt());
        payload.put("updatedAt", schedule.updatedAt());
        return payload;
    }

    private Map<String, Object> sessionPayload(SessionRecord session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", session.id());
        payload.put("displayName", session.displayName());
        payload.put("createdAt", session.createdAt());
        payload.put("updatedAt", session.updatedAt());
        payload.put("archived", session.archived());
        return payload;
    }

    private SessionRecord activeSession(String sessionId) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IOException("session is required");
        }
        return sessionIndex.get(sessionId)
                .filter(record -> !record.archived())
                .orElseThrow(() -> new IOException("session not found: " + sessionId));
    }

    private String sessionIdFromPayload(Map<?, ?> payload) {
        String sessionId = stringValue(payload, "sessionId");
        return sessionId.isBlank() ? currentSessionId : sessionId;
    }

    private AgentRuntime currentRuntime() throws IOException {
        if (!hasCurrentSession()) {
            throw new IOException("session is required");
        }
        return runtimeForActiveSession(currentSessionId);
    }

    private AgentRuntime runtimeForActiveSession(String sessionId) throws IOException {
        SessionRecord session = activeSession(sessionId);
        return runtimePool.runtimeFor(session.id());
    }

    /**
     * 切换当前 Web 选中的 session。
     *
     * <p>这里只更新当前指针并确保目标 runtime 存在，不关闭其他 session 的 runtime。
     * 因此用户切到 B 时，A 可以继续在后台执行。</p>
     */
    private void selectSession(SessionRecord session) throws IOException {
        synchronized (runtimeLock) {
            runtimePool.runtimeFor(session.id());
            currentSessionId = session.id();
            currentDisplayName = session.displayName();
        }
    }

    /**
     * Web 空启动时只恢复已有活跃 session，不再主动创建 default session。
     */
    private SessionRecord initialSession(String sessionName) throws IOException {
        if (sessionName != null && !sessionName.isBlank()) {
            return sessionIndex.ensure(sessionName, sessionName);
        }
        List<SessionRecord> sessions = sessionIndex.listActive();
        return sessions.isEmpty() ? null : sessions.getFirst();
    }

    /**
     * 首次发送消息时同步创建会话，用输入摘要作为展示名。
     */
    private SessionRecord createSessionForFirstMessage(String text) throws IOException {
        return sessionIndex.create(firstMessageDisplayName(text));
    }

    private String firstMessageDisplayName(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return "新会话";
        }
        return compact.length() > 18 ? compact.substring(0, 18) + "..." : compact;
    }

    private boolean hasCurrentSession() {
        return currentSessionId != null && !currentSessionId.isBlank();
    }

    private void clearCurrentSession() {
        currentSessionId = "";
        currentDisplayName = "";
    }

    private String messageSessionId(String path) {
        String prefix = "/api/sessions/";
        String suffix = "/messages";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        String raw = path.substring(prefix.length(), path.length() - suffix.length());
        if (raw.isBlank() || raw.contains("/")) {
            return null;
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private String roomMessageId(String path) {
        String prefix = "/api/rooms/";
        String suffix = "/messages";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        String raw = path.substring(prefix.length(), path.length() - suffix.length());
        if (raw.isBlank() || raw.contains("/")) {
            return null;
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private RoomMemberRoute roomMemberRoute(String path) {
        String prefix = "/api/rooms/";
        String marker = "/members";
        if (!path.startsWith(prefix)) {
            return null;
        }
        int markerIndex = path.indexOf(marker, prefix.length());
        if (markerIndex < 0) {
            return null;
        }
        String rawRoomId = path.substring(prefix.length(), markerIndex);
        if (rawRoomId.isBlank() || rawRoomId.contains("/")) {
            return null;
        }
        String suffix = path.substring(markerIndex + marker.length());
        String action;
        if (suffix.isBlank()) {
            action = "";
        } else if (suffix.startsWith("/") && suffix.indexOf('/', 1) < 0) {
            action = suffix.substring(1);
        } else {
            return null;
        }
        return new RoomMemberRoute(URLDecoder.decode(rawRoomId, StandardCharsets.UTF_8), action);
    }

    /**
     * 把历史消息压成前端可渲染的最小 DTO。
     */
    private Map<String, Object> messagePayload(Message message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role());
        payload.put("content", messageContent(message));
        if (message.hasToolCalls()) {
            payload.put("toolCalls", message.toolCalls().stream()
                    .map(this::toolCallPayload)
                    .toList());
        }
        if (message.toolCallId() != null && !message.toolCallId().isBlank()) {
            payload.put("toolCallId", message.toolCallId());
        }
        return payload;
    }

    private String messageContent(Message message) {
        if (message.content() != null) {
            return message.content();
        }
        return "";
    }

    private Map<String, Object> toolCallPayload(ToolCall toolCall) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", toolCall.id());
        if (toolCall.function() != null) {
            payload.put("name", toolCall.function().name());
            payload.put("argumentsJson", toolCall.function().argumentsJson());
        }
        return payload;
    }

    private Map<String, Object> approvalPayload(ToolApprovalRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalId", request.approvalId());
        payload.put("sessionName", request.sessionName());
        payload.put("runId", request.runId());
        payload.put("toolCallId", request.toolCallId());
        payload.put("toolName", request.toolName());
        payload.put("argumentsJson", request.argumentsJson());
        payload.put("reason", request.reason());
        payload.put("requestedAt", request.requestedAt() == null ? "" : request.requestedAt().toString());
        return payload;
    }

    private record RoomMemberRoute(String roomId, String action) {
    }

    private record ArchiveDeleteItem(String type, String id) {
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        sendText(exchange, status, objectMapper.writeValueAsString(body), "application/json; charset=utf-8");
    }

    private int sessionErrorStatus(IOException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.startsWith("agent is running")) {
            return 409;
        }
        if (message.startsWith("session not found")) {
            return 404;
        }
        return 400;
    }

    private int roomErrorStatus(IOException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.startsWith("room not found") || message.startsWith("room agent not found") || message.startsWith("room member not found")) {
            return 404;
        }
        if (message.startsWith("room archived")) {
            return 409;
        }
        return 400;
    }

    private int archiveErrorStatus(IOException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("not found")) {
            return 404;
        }
        if (message.contains("must be archived")) {
            return 409;
        }
        return 400;
    }

    private void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        sendBytes(exchange, status, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private void sendBytes(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }

    @Override
    public void close() {
        server.stop(0);
        clients.close();
        synchronized (runtimeLock) {
            runtimePool.close();
        }
        heartbeat.shutdownNow();
        executor.shutdownNow();
    }
}
