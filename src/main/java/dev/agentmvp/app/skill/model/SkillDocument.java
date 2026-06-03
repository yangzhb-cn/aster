package dev.agentmvp.app.skill.model;

/**
 * 完整 Skill 文档。
 *
 * <p>只有当 LLM 调用 load_skill 时，才会把完整 SKILL.md 读出来。
 * 这就是渐进式加载：索引常驻上下文，正文按需进入上下文。</p>
 */
public record SkillDocument(
        String name,
        String content
) {
}
