package com.aster.ui.tui.command;

import com.aster.app.runtime.WorkspacePaths;
import com.aster.core.session.SessionIndex;
import com.aster.core.session.model.SessionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * session 管理命令。
 */
public class SessionCommand implements SlashCommand {
    private static final DateTimeFormatter SESSION_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final List<SlashCommandOption> OPTIONS = List.of(
            new SlashCommandOption("/session list", "/session list", "列出本地会话", false),
            new SlashCommandOption("/session new", "/session new", "创建新会话", false),
            new SlashCommandOption("/session new <displayName>", "/session new ", "创建指定展示名的新会话", true),
            new SlashCommandOption("/session use <id>", "/session use ", "切换到已有会话", true),
            new SlashCommandOption("/session delete <id>", "/session delete ", "归档会话", true),
            new SlashCommandOption("/session current", "/session current", "显示当前会话", false)
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<SlashCommandOption> options() {
        return OPTIONS;
    }

    @Override
    public boolean matches(String input) {
        return input.equals("/session") || input.startsWith("/session ");
    }

    @Override
    public void handle(SlashCommandContext context, String input) throws IOException {
        String[] parts = input.trim().split("\\s+");
        String subcommand = parts.length >= 2 ? parts[1] : "help";

        switch (subcommand) {
            case "list" -> showSessionList(context);
            case "new" -> createSession(context, input);
            case "use" -> useSession(context, parts);
            case "delete" -> deleteSession(context, parts);
            case "current" -> showCurrentSession(context);
            default -> showSessionHelp(context);
        }
    }

    private void showSessionList(SlashCommandContext context) throws IOException {
        List<SessionRecord> sessions = sessionIndex().listActive();
        if (sessions.isEmpty()) {
            context.window().addSystemBlock("sessions: empty");
            context.window().setStatus("no sessions");
            return;
        }

        String current = context.window().currentSessionName();
        StringBuilder text = new StringBuilder("sessions:\n");
        for (SessionRecord session : sessions) {
            String marker = session.id().equals(current) ? "* " : "  ";
            text.append(marker)
                    .append(session.displayName())
                    .append(" | id=")
                    .append(session.id())
                    .append(" | ")
                    .append(formatTime(session.updatedAt()))
                    .append("\n");
        }
        context.window().addSystemBlock(text.toString().stripTrailing());
        context.window().setStatus("session list");
    }

    private void createSession(SlashCommandContext context, String input) throws IOException {
        context.window().ensureAgentIsIdle();
        String displayName = input.length() > "/session new".length()
                ? input.substring("/session new".length()).trim()
                : "";
        SessionRecord session = sessionIndex().create(displayName);

        context.window().switchRuntime(session.id(), true);
        context.window().setStatus("new session: " + session.displayName());
    }

    private void useSession(SlashCommandContext context, String[] parts) throws IOException {
        context.window().ensureAgentIsIdle();
        if (parts.length < 3) {
            throw new IOException("usage: /session use <id>");
        }
        String sessionId = parts[2];
        SessionRecord session = sessionIndex().get(sessionId)
                .filter(record -> !record.archived())
                .orElseThrow(() -> new IOException("session not found: " + sessionId));

        context.window().switchRuntime(session.id(), true);
        context.window().setStatus("session: " + session.displayName());
    }

    private void deleteSession(SlashCommandContext context, String[] parts) throws IOException {
        context.window().ensureAgentIsIdle();
        if (parts.length < 3) {
            throw new IOException("usage: /session delete <id>");
        }
        String sessionId = parts[2];
        if (sessionId.equals(context.window().currentSessionName())) {
            throw new IOException("不能删除当前会话，请先 /session use <id> 切换到其他会话");
        }
        SessionRecord session = sessionIndex().archive(sessionId);

        context.window().addSystemBlock("archived session: " + session.displayName() + " | id=" + session.id());
        context.window().setStatus("session archived");
    }

    private void showCurrentSession(SlashCommandContext context) throws IOException {
        String current = context.window().currentSessionName();
        String displayName = sessionIndex().get(current)
                .map(SessionRecord::displayName)
                .orElse(current);
        context.window().addSystemBlock("current session: " + displayName + " | id=" + current);
        context.window().setStatus("session current");
    }

    private void showSessionHelp(SlashCommandContext context) {
        context.window().addSystemBlock("""
                session commands:
                /session list
                /session new [displayName]
                /session use <id>
                /session delete <id>
                /session current
                """.stripTrailing());
        context.window().setStatus("session help");
    }

    private SessionIndex sessionIndex() {
        return new SessionIndex(objectMapper, WorkspacePaths.SESSIONS);
    }

    private String formatTime(String timestamp) {
        return SESSION_TIME_FORMATTER.format(Instant.parse(timestamp));
    }
}
