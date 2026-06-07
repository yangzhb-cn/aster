package com.aster.app.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent 运行时工作区路径。
 *
 * <p>教学版把所有附加数据都放在项目根目录的 workspace 下：
 * MCP 配置、Skill 目录、session JSONL、后续长期记忆都在这里收口。</p>
 */
public final class WorkspacePaths {
    public static final Path ROOT = Path.of("workspace");
    public static final Path MCP_CONFIG = ROOT.resolve("mcp.json");
    public static final Path SKILLS = ROOT.resolve("skills");
    public static final Path SESSIONS = ROOT.resolve("sessions");
    public static final Path CONTEXT_WINDOWS = ROOT.resolve("context-windows");
    public static final Path MEMORY = ROOT.resolve("memory");
    public static final Path LONG_TERM_MEMORY = MEMORY.resolve("long-term-memory.md");
    public static final Path TASKS = ROOT.resolve("tasks");
    public static final Path BACKGROUND_TASKS = TASKS.resolve("tasks.jsonl");
    public static final Path BACKGROUND_TASK_RUNS = TASKS.resolve("runs.jsonl");
    public static final Path SCHEDULES = ROOT.resolve("schedules");
    public static final Path SCHEDULE_FILE = SCHEDULES.resolve("schedules.json");
    public static final Path TODOS = ROOT.resolve("todos");
    public static final Path TODO_FILE = TODOS.resolve("todos.json");
    public static final Path ARTIFACTS = ROOT.resolve("artifacts");
    public static final Path TOOL_RESULTS = ARTIFACTS.resolve("tool-results");
    public static final Path IM = ROOT.resolve("im");
    public static final Path TELEGRAM_SESSION_MAP = IM.resolve("telegram-sessions.json");
    public static final Path ROOMS = ROOT.resolve("rooms");
    public static final Path ROOM_INDEX = ROOMS.resolve("rooms.json");
    public static final Path ROOM_MEMBERS = ROOMS.resolve("members.json");
    public static final Path ROOM_MESSAGES = ROOMS.resolve("messages");
    public static final Path ROOM_AGENTS = ROOMS.resolve("agents");
    public static final Path ROOM_AGENT_INDEX = ROOM_AGENTS.resolve("agents.json");
    public static final Path ROOM_AGENT_PROMPTS = ROOM_AGENTS.resolve("prompts");
    public static final Path ROOM_AGENT_SESSIONS = ROOMS.resolve("agent-sessions");

    private WorkspacePaths() {
    }

    /**
     * 创建基础工作区目录。
     */
    public static void ensureDirectories() throws IOException {
        Files.createDirectories(ROOT);
        Files.createDirectories(SKILLS);
        Files.createDirectories(SESSIONS);
        Files.createDirectories(CONTEXT_WINDOWS);
        Files.createDirectories(MEMORY);
        Files.createDirectories(TASKS);
        Files.createDirectories(SCHEDULES);
        Files.createDirectories(TODOS);
        Files.createDirectories(ARTIFACTS);
        Files.createDirectories(TOOL_RESULTS);
        Files.createDirectories(IM);
        Files.createDirectories(ROOMS);
        Files.createDirectories(ROOM_MESSAGES);
        Files.createDirectories(ROOM_AGENTS);
        Files.createDirectories(ROOM_AGENT_PROMPTS);
        Files.createDirectories(ROOM_AGENT_SESSIONS);
    }
}
