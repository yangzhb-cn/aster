package com.aster.ui.tui.command;

import com.aster.app.runtime.WorkspacePaths;
import com.aster.core.session.SessionCatalog;
import com.aster.core.session.model.SessionSummary;

import java.io.IOException;
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
            new SlashCommandOption("/session new", "/session new", "创建自动命名新会话", false),
            new SlashCommandOption("/session new <name>", "/session new ", "创建指定名称新会话", true),
            new SlashCommandOption("/session use <name>", "/session use ", "切换到已有会话", true),
            new SlashCommandOption("/session current", "/session current", "显示当前会话", false)
    );

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
            case "new" -> createSession(context, parts);
            case "use" -> useSession(context, parts);
            case "current" -> showCurrentSession(context);
            default -> showSessionHelp(context);
        }
    }

    private void showSessionList(SlashCommandContext context) throws IOException {
        List<SessionSummary> sessions = SessionCatalog.list(WorkspacePaths.SESSIONS);
        if (sessions.isEmpty()) {
            context.window().addSystemBlock("sessions: empty");
            context.window().setStatus("no sessions");
            return;
        }

        String current = context.window().currentSessionName();
        StringBuilder text = new StringBuilder("sessions:\n");
        for (SessionSummary session : sessions) {
            String marker = session.name().equals(current) ? "* " : "  ";
            text.append(marker)
                    .append(session.name())
                    .append(" | ")
                    .append(formatBytes(session.sizeBytes()))
                    .append(" | ")
                    .append(SESSION_TIME_FORMATTER.format(session.modifiedAt()))
                    .append("\n");
        }
        context.window().addSystemBlock(text.toString().stripTrailing());
        context.window().setStatus("session list");
    }

    private void createSession(SlashCommandContext context, String[] parts) throws IOException {
        context.window().ensureAgentIsIdle();
        String sessionName = parts.length >= 3
                ? parts[2]
                : SessionCatalog.generateName(WorkspacePaths.SESSIONS);
        SessionCatalog.requireValidName(sessionName);
        if (SessionCatalog.exists(WorkspacePaths.SESSIONS, sessionName)) {
            throw new IOException("session already exists: " + sessionName + "，使用 /session use " + sessionName + " 切换");
        }

        context.window().switchRuntime(sessionName, true);
        context.window().setStatus("new session: " + sessionName);
    }

    private void useSession(SlashCommandContext context, String[] parts) throws IOException {
        context.window().ensureAgentIsIdle();
        if (parts.length < 3) {
            throw new IOException("usage: /session use <name>");
        }
        String sessionName = parts[2];
        SessionCatalog.requireValidName(sessionName);
        if (!SessionCatalog.exists(WorkspacePaths.SESSIONS, sessionName)) {
            throw new IOException("session not found: " + sessionName);
        }

        context.window().switchRuntime(sessionName, true);
        context.window().setStatus("session: " + sessionName);
    }

    private void showCurrentSession(SlashCommandContext context) {
        context.window().addSystemBlock("current session: " + context.window().currentSessionName());
        context.window().setStatus("session current");
    }

    private void showSessionHelp(SlashCommandContext context) {
        context.window().addSystemBlock("""
                session commands:
                /session list
                /session new [name]
                /session use <name>
                /session current
                """.stripTrailing());
        context.window().setStatus("session help");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
