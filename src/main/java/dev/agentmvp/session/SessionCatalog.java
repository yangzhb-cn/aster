package dev.agentmvp.session;

import dev.agentmvp.session.model.SessionSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * workspace/sessions 下的 session 文件目录。
 *
 * <p>它只负责文件层面的事情：生成名称、校验名称、列出 jsonl 文件、定位路径。
 * 具体事件读写仍然交给 JsonlSessionStore。</p>
 */
public final class SessionCatalog {
    public static final String DEFAULT_SESSION = "default";

    private static final Pattern SESSION_NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final DateTimeFormatter NAME_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private SessionCatalog() {
    }

    /**
     * 生成一个不会和现有文件冲突的新 session 名。
     */
    public static String generateName(Path sessionsDirectory) throws IOException {
        String base = "session-" + NAME_TIME_FORMATTER.format(Instant.now());
        String candidate = base;
        int index = 2;
        while (Files.exists(fileFor(sessionsDirectory, candidate))) {
            candidate = base + "-" + index;
            index++;
        }
        return candidate;
    }

    /**
     * 根据 session 名得到 JSONL 文件路径。
     */
    public static Path fileFor(Path sessionsDirectory, String sessionName) {
        requireValidName(sessionName);
        return sessionsDirectory.resolve(sessionName + ".jsonl").normalize();
    }

    /**
     * 判断 session 文件是否存在。
     */
    public static boolean exists(Path sessionsDirectory, String sessionName) {
        return Files.isRegularFile(fileFor(sessionsDirectory, sessionName));
    }

    /**
     * 列出所有本地 session，按最近修改时间倒序。
     */
    public static List<SessionSummary> list(Path sessionsDirectory) throws IOException {
        Files.createDirectories(sessionsDirectory);
        try (var stream = Files.list(sessionsDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .map(SessionCatalog::summary)
                    .sorted(Comparator.comparing(SessionSummary::modifiedAt).reversed())
                    .toList();
        }
    }

    /**
     * 校验 session 名，避免路径穿越和难读文件名。
     */
    public static void requireValidName(String sessionName) {
        if (sessionName == null || !SESSION_NAME.matcher(sessionName).matches()) {
            throw new IllegalArgumentException("session name 只能包含字母、数字、点、下划线、短横线，长度 1 到 64");
        }
    }

    private static SessionSummary summary(Path file) {
        try {
            String fileName = file.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - ".jsonl".length());
            return new SessionSummary(
                    name,
                    file,
                    Files.size(file),
                    Files.getLastModifiedTime(file).toInstant()
            );
        } catch (IOException e) {
            throw new IllegalStateException("读取 session 文件信息失败: " + file, e);
        }
    }
}
