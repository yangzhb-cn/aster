package dev.agentmvp.app.skill;

import dev.agentmvp.app.skill.model.SkillMetadata;

import java.util.List;

/**
 * 把 Skill 索引渲染成 system prompt 片段。
 *
 * <p>这里不会渲染完整 SKILL.md，只渲染 name 和 description。
 * 完整内容必须由模型通过 load_skill 工具按需加载。</p>
 */
public class SkillIndexRenderer {
    /**
     * 根据 Skill 索引生成 system prompt 文本。
     */
    public String render(List<SkillMetadata> skills) {
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        text.append("当前可用 Skills：\n\n");
        for (SkillMetadata skill : skills) {
            text.append("- ")
                    .append(skill.name())
                    .append("：")
                    .append(skill.description())
                    .append("\n");
        }

        text.append("""

                Skill 使用规则：

                - 如果你认为某个 Skill 对当前任务有帮助，先调用 load_skill(name) 读取完整 SKILL.md。
                - 不要凭空猜测 Skill 的完整内容；没有加载前，只能依据上面的 name 和 description 判断是否相关。
                - references 和 scripts 不会自动加载，需要时再用 read 或 bash 工具访问。
                - load_skill 只负责读取 SKILL.md，不会执行脚本，也不会自动读取 references。
                """);

        return text.toString().stripTrailing();
    }
}
