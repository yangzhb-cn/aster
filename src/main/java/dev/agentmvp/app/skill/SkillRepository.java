package dev.agentmvp.app.skill;

import dev.agentmvp.app.skill.model.SkillDocument;
import dev.agentmvp.app.skill.model.SkillMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 Skill 仓库。
 *
 * <p>教学版只支持一种规范：每个 Skill 是 skills 目录下的一级子目录，
 * 子目录里必须有 SKILL.md。启动时只解析 SKILL.md 顶部 frontmatter 的
 * name 和 description，完整正文等 LLM 调用 load_skill 时再读取。</p>
 */
public class SkillRepository {
    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final Map<String, SkillMetadata> skillsByName;

    private SkillRepository(Map<String, SkillMetadata> skillsByName) {
        this.skillsByName = new LinkedHashMap<>(skillsByName);
    }

    /**
     * 扫描本地 skills 目录。
     *
     * <p>目录不存在时返回空仓库，方便项目一开始没有任何 Skill 也能正常启动。</p>
     */
    public static SkillRepository scan(Path skillsDirectory) throws IOException {
        Path root = skillsDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new SkillRepository(Map.of());
        }

        List<Path> skillFiles = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream
                    .filter(Files::isDirectory)
                    .map(directory -> directory.resolve(SKILL_FILE_NAME))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getParent().getFileName().toString()))
                    .forEach(skillFiles::add);
        }

        Map<String, SkillMetadata> result = new LinkedHashMap<>();
        for (Path skillFile : skillFiles) {
            SkillMetadata metadata = parseMetadata(skillFile);
            if (result.containsKey(metadata.name())) {
                throw new IllegalArgumentException("重复的 Skill name: " + metadata.name());
            }
            result.put(metadata.name(), metadata);
        }

        return new SkillRepository(result);
    }

    /**
     * 列出已扫描到的 Skill 索引。
     */
    public List<SkillMetadata> listMetadata() {
        return List.copyOf(skillsByName.values());
    }

    /**
     * 按 Skill name 加载完整 SKILL.md。
     *
     * <p>这里故意不接受路径，只接受扫描阶段登记过的 name。
     * load_skill 是 Skill 加载工具，不是通用文件读取工具。</p>
     */
    public SkillDocument load(String name) throws IOException {
        SkillMetadata metadata = skillsByName.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("未知 Skill: " + name);
        }

        String content = Files.readString(metadata.skillFile(), StandardCharsets.UTF_8);
        return new SkillDocument(metadata.name(), content);
    }

    /**
     * 判断仓库里是否存在至少一个 Skill。
     */
    public boolean isEmpty() {
        return skillsByName.isEmpty();
    }

    private static SkillMetadata parseMetadata(Path skillFile) throws IOException {
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        List<String> lines = content.lines().toList();
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            throw new IllegalArgumentException("SKILL.md 缺少 frontmatter: " + skillFile);
        }

        Map<String, String> values = new LinkedHashMap<>();
        boolean closed = false;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.equals("---")) {
                closed = true;
                break;
            }
            if (line.isEmpty()) {
                continue;
            }

            int split = line.indexOf(':');
            if (split <= 0) {
                continue;
            }

            String key = line.substring(0, split).trim();
            String value = stripQuotes(line.substring(split + 1).trim());
            values.put(key, value);
        }

        if (!closed) {
            throw new IllegalArgumentException("SKILL.md frontmatter 没有结束标记: " + skillFile);
        }

        String name = requireFrontmatterValue(values, "name", skillFile);
        String description = requireFrontmatterValue(values, "description", skillFile);
        return new SkillMetadata(name, description, skillFile.toAbsolutePath().normalize());
    }

    private static String requireFrontmatterValue(Map<String, String> values, String key, Path skillFile) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SKILL.md 缺少 " + key + ": " + skillFile);
        }
        return value;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
