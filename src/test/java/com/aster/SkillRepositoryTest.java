package com.aster;

import com.aster.app.skill.SkillIndexRenderer;
import com.aster.app.skill.SkillRepository;
import com.aster.app.skill.model.SkillDocument;
import com.aster.app.skill.model.SkillMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill 适配层测试。
 *
 * <p>这里验证的是“渐进式加载”：启动时只读取索引，
 * 完整 SKILL.md 必须等 load_skill 这类能力按需加载。</p>
 */
class SkillRepositoryTest {
    @TempDir
    Path tempDir;

    /**
     * 验证 SkillRepository 只解析 SKILL.md 的 name 和 description。
     */
    @Test
    void scansSkillMetadataFromFrontmatter() throws Exception {
        Path skillsDirectory = tempDir.resolve("skills");
        writeSkill(skillsDirectory, "web-access", "web-access", "网页访问经验", "完整正文内容");

        SkillRepository repository = SkillRepository.scan(skillsDirectory);

        List<SkillMetadata> metadata = repository.listMetadata();
        assertEquals(1, metadata.size());
        assertEquals("web-access", metadata.getFirst().name());
        assertEquals("网页访问经验", metadata.getFirst().description());
    }

    /**
     * 验证 Skill 索引只包含轻量信息，不把完整正文提前塞进请求前提醒。
     */
    @Test
    void rendersOnlySkillIndexForSystemReminder() throws Exception {
        Path skillsDirectory = tempDir.resolve("skills");
        writeSkill(skillsDirectory, "web-access", "web-access", "网页访问经验", "不要提前出现的完整正文");

        SkillRepository repository = SkillRepository.scan(skillsDirectory);
        String prompt = new SkillIndexRenderer().render(repository.listMetadata());

        assertTrue(prompt.contains("web-access"));
        assertTrue(prompt.contains("网页访问经验"));
        assertTrue(prompt.contains("load_skill"));
        assertFalse(prompt.contains("不要提前出现的完整正文"));
    }

    /**
     * 验证按 name 加载完整 SKILL.md，并拒绝未知 Skill。
     */
    @Test
    void loadsFullSkillDocumentByName() throws Exception {
        Path skillsDirectory = tempDir.resolve("skills");
        writeSkill(skillsDirectory, "web-access", "web-access", "网页访问经验", "完整正文内容");

        SkillRepository repository = SkillRepository.scan(skillsDirectory);
        SkillDocument document = repository.load("web-access");

        assertEquals("web-access", document.name());
        assertTrue(document.content().contains("完整正文内容"));
        assertThrows(IllegalArgumentException.class, () -> repository.load("../other"));
    }

    private void writeSkill(Path skillsDirectory, String directoryName, String name, String description, String body) throws Exception {
        Path skillDirectory = skillsDirectory.resolve(directoryName);
        Files.createDirectories(skillDirectory);
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
                ---
                name: %s
                description: %s
                ---

                # %s

                %s
                """.formatted(name, description, name, body));
    }
}
