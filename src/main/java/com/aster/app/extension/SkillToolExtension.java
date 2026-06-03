package com.aster.app.extension;

import com.aster.app.tool.builtin.LoadSkillTool;

import java.nio.file.Path;

/**
 * Skill 工具扩展。
 *
 * <p>load_skill 属于 Skill 能力，不再放进四个基础内置工具集合里。</p>
 */
public class SkillToolExtension implements AsterRuntimeExtension {
    /**
     * 在存在本地 Skill 时注册 load_skill 工具。
     */
    @Override
    public void registerTools(RuntimeExtensionContext context) {
        if (!context.skillRepository().isEmpty()) {
            new LoadSkillTool(Path.of("."), context.skillRepository()).registerTo(context.toolRegistry());
        }
    }
}
