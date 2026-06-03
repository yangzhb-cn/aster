package dev.agentmvp.memory;

import dev.agentmvp.memory.model.MemoryCandidate;
import dev.agentmvp.memory.model.MemoryType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Markdown 长期记忆存储。
 *
 * <p>长期记忆最终是给人和 Agent 都能读的文本，所以第一版直接存成 Markdown。
 * 但写入入口仍然接收结构化 MemoryCandidate，并且只允许四种固定类型。</p>
 */
public class MarkdownMemoryStore {
    private static final String TITLE = "# 长期记忆";

    private final Path memoryFile;

    public MarkdownMemoryStore(Path memoryFile) throws IOException {
        this.memoryFile = Objects.requireNonNull(memoryFile);
        ensureFile();
    }

    /**
     * 读取完整 Markdown 长期记忆。
     */
    public synchronized String load() throws IOException {
        ensureFile();
        return Files.readString(memoryFile, StandardCharsets.UTF_8);
    }

    /**
     * 追加候选记忆，返回本次新增条数。
     *
     * <p>第一版只做简单去重：如果当前 Markdown 已经包含同样 content，
     * 就跳过，避免每轮后台抽取把同一句话重复写进去。</p>
     */
    public synchronized int appendCandidates(List<MemoryCandidate> candidates) throws IOException {
        ensureFile();
        String markdown = load();
        int added = 0;

        for (MemoryCandidate candidate : candidates) {
            validate(candidate);
            String content = normalizeLine(candidate.content());
            if (containsMemory(markdown, content)) {
                continue;
            }

            String entry = renderEntry(content, normalizeLine(candidate.evidence()));
            markdown = appendToSection(markdown, candidate.type(), entry);
            added++;
        }

        if (added > 0) {
            Files.writeString(memoryFile, markdown, StandardCharsets.UTF_8);
        }
        return added;
    }

    /**
     * 判断 Markdown 里是否已经有真实记忆条目。
     */
    public boolean hasMemoryContent(String markdown) {
        return markdown != null && markdown.lines().anyMatch(line -> line.stripLeading().startsWith("- "));
    }

    private void ensureFile() throws IOException {
        Files.createDirectories(memoryFile.getParent());
        if (!Files.exists(memoryFile)) {
            Files.writeString(memoryFile, defaultMarkdown(), StandardCharsets.UTF_8);
        }
    }

    private String defaultMarkdown() {
        StringBuilder builder = new StringBuilder(TITLE).append("\n\n");
        for (MemoryType type : MemoryType.values()) {
            builder.append("## ").append(type.heading()).append("\n\n");
        }
        return builder.toString();
    }

    private void validate(MemoryCandidate candidate) {
        if (candidate == null || candidate.type() == null) {
            throw new IllegalArgumentException("长期记忆类型不能为空");
        }
        if (candidate.content() == null || candidate.content().isBlank()) {
            throw new IllegalArgumentException("长期记忆内容不能为空");
        }
        if (candidate.evidence() == null || candidate.evidence().isBlank()) {
            throw new IllegalArgumentException("长期记忆必须带证据");
        }
    }

    private boolean containsMemory(String markdown, String content) {
        return markdown.contains("- " + content + "\n")
                || markdown.contains("- " + content + "\r\n");
    }

    private String renderEntry(String content, String evidence) {
        return """
                - %s
                  - 证据：%s
                  - 更新时间：%s
                """.formatted(content, evidence, LocalDate.now());
    }

    private String appendToSection(String markdown, MemoryType type, String entry) {
        String heading = "## " + type.heading();
        int headingStart = markdown.indexOf(heading);
        if (headingStart < 0) {
            return ensureTrailingBlankLine(markdown)
                    + heading
                    + "\n\n"
                    + entry
                    + "\n";
        }

        int sectionContentStart = markdown.indexOf('\n', headingStart);
        if (sectionContentStart < 0) {
            return markdown + "\n\n" + entry;
        }

        int nextHeading = markdown.indexOf("\n## ", sectionContentStart + 1);
        int insertAt = nextHeading < 0 ? markdown.length() : nextHeading + 1;
        String before = markdown.substring(0, insertAt);
        String after = markdown.substring(insertAt);
        return ensureTrailingBlankLine(before) + entry + "\n" + after;
    }

    private String ensureTrailingBlankLine(String text) {
        if (text.endsWith("\n\n")) {
            return text;
        }
        if (text.endsWith("\n")) {
            return text + "\n";
        }
        return text + "\n\n";
    }

    private String normalizeLine(String value) {
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
