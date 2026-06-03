package com.aster.app.tool.builtin;

import com.aster.llm.model.ToolCall;
import com.aster.app.skill.SkillRepository;
import com.aster.app.skill.model.SkillDocument;
import com.aster.app.tool.builtin.model.LoadSkillToolParams;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * load_skill 工具：按 Skill name 加载完整 SKILL.md。
 *
 * <p>它不是 Skill 执行器，也不会自动读取 references 或执行 scripts。
 * 它只负责把完整 Skill 文档返回给 LLM，后续读取资料和运行脚本继续复用 read/bash。</p>
 */
public class LoadSkillTool extends AbstractBuiltinTool {
    private final SkillRepository skillRepository;

    public LoadSkillTool(Path workingDirectory, SkillRepository skillRepository) {
        super(workingDirectory);
        this.skillRepository = skillRepository;
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "load_skill",
                "LoadSkill",
                "按 Skill 名称加载完整 SKILL.md 内容，用于渐进式读取 Skill 说明。",
                objectSchema(
                        Map.of(
                                "name", stringSchema("要加载的 Skill 名称，只能是 system prompt 中列出的 Skill name")
                        ),
                        List.of("name")
                )
        );
    }

    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        LoadSkillToolParams params = LoadSkillToolParams.from(arguments);
        SkillDocument document = skillRepository.load(params.name());
        return ToolResult.text(call.id(), document.content());
    }
}
