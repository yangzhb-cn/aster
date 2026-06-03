package com.aster.ui.web;

import com.aster.app.runtime.AgentRuntime;
import com.aster.app.runtime.AgentRuntimeFactory;
import com.aster.app.runtime.WorkspacePaths;
import com.aster.app.hitl.model.ToolApprovalRequest;
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
    private final AgentRuntimeFactory runtimeFactory;
    private final WebAgentEventHandler eventHandler;
    private final WebNotificationSink notificationSink;
    private final SessionIndex sessionIndex;
    private final TodoStore todoStore;
    private final Object runtimeLock = new Object();
    private final HttpServer server;
    private AgentRuntime runtime;
    private String currentSessionId;
    private String currentDisplayName;

    public WebServer(int port, AgentRuntimeFactory runtimeFactory, String sessionName) throws IOException {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory);
        this.eventHandler = new WebAgentEventHandler(objectMapper, clients);
        this.notificationSink = new WebNotificationSink(objectMapper, clients);
        this.sessionIndex = new SessionIndex(objectMapper, WorkspacePaths.SESSIONS);
        this.todoStore = new JsonTodoStore(objectMapper, WorkspacePaths.TODO_FILE);
        SessionRecord session = sessionIndex.ensure(sessionName, sessionName);
        this.currentSessionId = session.id();
        this.currentDisplayName = session.displayName();
        this.runtime = runtimeFactory.create(eventHandler, notificationSink, session.id());
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
        server.createContext("/api/approvals", this::handleApprovals);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/todos", this::handleTodos);
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

        String text = readTextPayload(exchange);
        if (text.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "text is required"));
            return;
        }

        synchronized (runtimeLock) {
            runtime.submit(text);
            sessionIndex.touch(currentSessionId);
        }
        sendJson(exchange, 202, statusPayload());
    }

    private void handleSteer(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        String text = readTextPayload(exchange);
        if (text.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "text is required"));
            return;
        }

        boolean accepted;
        synchronized (runtimeLock) {
            accepted = runtime.steer(text);
        }
        sendJson(exchange, accepted ? 202 : 409, statusPayload());
    }

    private void handleStop(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        boolean accepted;
        synchronized (runtimeLock) {
            accepted = runtime.stop();
        }
        sendJson(exchange, accepted ? 202 : 409, statusPayload());
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

        String path = exchange.getRequestURI().getPath();
        Map<?, ?> payload = readPayload(exchange);
        String approvalId = stringValue(payload, "id");
        boolean accepted;
        int count = 0;
        synchronized (runtimeLock) {
            if ("/api/approvals/approve".equals(path)) {
                if (approvalId.isBlank()) {
                    count = runtime.approveAllTools();
                    accepted = count > 0;
                } else {
                    accepted = runtime.approveTool(approvalId);
                    count = accepted ? 1 : 0;
                }
            } else if ("/api/approvals/deny".equals(path)) {
                String reason = stringValue(payload, "reason");
                if (approvalId.isBlank()) {
                    count = runtime.denyAllTools(reason.isBlank() ? "用户拒绝全部待审批工具" : reason);
                    accepted = count > 0;
                } else {
                    accepted = runtime.denyTool(approvalId, reason);
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
    }

    /**
     * 处理 Web 会话 CRUD。
     *
     * <p>这里只管理 session 索引和当前 runtime 切换；真实消息仍由 JsonlSessionStore 读写。</p>
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

    private void createSession(HttpExchange exchange) throws IOException {
        ensureIdle();
        String displayName = readStringPayload(exchange, "displayName");
        SessionRecord session = sessionIndex.create(displayName);
        switchRuntime(session);
        sendJson(exchange, 201, sessionsPayload());
    }

    private void useSession(HttpExchange exchange) throws IOException {
        ensureIdle();
        String sessionId = readRequiredPayload(exchange, "id");
        SessionRecord session = activeSession(sessionId);
        switchRuntime(session);
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
        ensureIdle();
        String sessionId = readRequiredPayload(exchange, "id");
        sessionIndex.archive(sessionId);

        synchronized (runtimeLock) {
            if (sessionId.equals(currentSessionId)) {
                List<SessionRecord> sessions = sessionIndex.listActive();
                SessionRecord next = sessions.isEmpty()
                        ? sessionIndex.create("新会话")
                        : sessions.getFirst();
                switchRuntime(next);
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

    private Map<String, Object> statusPayload() {
        Map<String, Object> status = new LinkedHashMap<>();
        synchronized (runtimeLock) {
            status.put("sessionId", currentSessionId);
            status.put("sessionName", currentSessionId);
            status.put("displayName", currentDisplayName);
            status.put("model", runtime.provider().defaultModel());
            status.put("provider", runtime.provider().name());
            status.put("skillCount", runtime.skillCount());
            status.put("busy", runtime.isBusy());
            status.put("queuedCount", runtime.queuedCount());
            status.put("pendingApprovals", runtime.pendingToolApprovals().stream()
                    .map(this::approvalPayload)
                    .toList());
        }
        status.put("sseClients", clients.size());
        return status;
    }

    private Map<String, Object> sessionsPayload() throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessions", sessionIndex.listActive());
        payload.put("currentSessionId", currentSessionId);
        payload.put("status", statusPayload());
        return payload;
    }

    private Map<String, Object> todosPayload() throws IOException {
        return Map.of("todos", todoStore.listActive().stream()
                .map(this::todoPayload)
                .toList());
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

    private SessionRecord activeSession(String sessionId) throws IOException {
        return sessionIndex.get(sessionId)
                .filter(record -> !record.archived())
                .orElseThrow(() -> new IOException("session not found: " + sessionId));
    }

    private void ensureIdle() throws IOException {
        synchronized (runtimeLock) {
            if (runtime.isBusy()) {
                throw new IOException("agent is running, wait for current request to finish");
            }
        }
    }

    /**
     * 切换当前 Web runtime 到指定 session。
     *
     * <p>HttpServer、SSE 客户端和事件 handler 都保持不变，只替换底层 AgentRuntime。</p>
     */
    private void switchRuntime(SessionRecord session) throws IOException {
        synchronized (runtimeLock) {
            if (runtime.isBusy()) {
                throw new IOException("agent is running, wait for current request to finish");
            }
            AgentRuntime oldRuntime = runtime;
            AgentRuntime nextRuntime = runtimeFactory.create(eventHandler, notificationSink, session.id());
            runtime = nextRuntime;
            currentSessionId = session.id();
            currentDisplayName = session.displayName();
            if (oldRuntime != null) {
                oldRuntime.close();
            }
        }
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
            runtime.close();
        }
        heartbeat.shutdownNow();
        executor.shutdownNow();
    }
}
