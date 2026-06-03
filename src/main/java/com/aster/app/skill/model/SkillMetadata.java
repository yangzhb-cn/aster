package com.aster.app.skill.model;

import java.nio.file.Path;

/**
 * Skill 的轻量索引信息。
 *
 * <p>这部分内容会进入请求前系统提醒，所以只保留 name 和 description。
 * skillFile 只给宿主程序定位完整 SKILL.md，不直接暴露给 LLM 当作任意路径读取入口。</p>
 */
public record SkillMetadata(
        String name,
        String description,
        Path skillFile
) {
}
