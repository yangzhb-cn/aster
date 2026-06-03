package com.aster.app.tool.builtin.model;

import java.util.Map;

/**
 * load_skill 工具的入参。
 *
 * <p>只接收 Skill name，不接收文件路径。
 * 这样可以保证它只能加载 SkillRepository 扫描过的 SKILL.md。</p>
 */
public record LoadSkillToolParams(String name) {
    /**
     * 从工具参数 Map 构造 load_skill 入参。
     */
    public static LoadSkillToolParams from(Map<String, Object> arguments) {
        return new LoadSkillToolParams(
                ToolParamReader.requiredString(arguments, "name")
        );
    }
}
