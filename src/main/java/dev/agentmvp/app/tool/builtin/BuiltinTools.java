package dev.agentmvp.app.tool.builtin;

import dev.agentmvp.app.skill.SkillRepository;
import dev.agentmvp.core.tool.ToolRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置工具注册入口。
 *
 * <p>这个类不实现具体工具逻辑，只负责组装默认工具列表并注册。
 * 以后新增工具时，新增一个 BuiltinTool 实现类，然后放进 defaultTools 即可。</p>
 */
public final class BuiltinTools {
    private BuiltinTools() {
    }

    /**
     * 注册 read、write、bash、edit 四个基础内置工具。
     */
    public static void registerAll(ToolRegistry toolRegistry, Path workingDirectory) {
        for (BuiltinTool tool : defaultTools(workingDirectory)) {
            tool.registerTo(toolRegistry);
        }
    }

    /**
     * 注册基础内置工具，并在存在 Skill 时额外注册 load_skill。
     *
     * <p>load_skill 属于 Skill 适配层的一部分，但执行上仍然是本地工具，
     * 所以最终也统一走 ToolRegistry.registerLocal。</p>
     */
    public static void registerAll(ToolRegistry toolRegistry, Path workingDirectory, SkillRepository skillRepository) {
        for (BuiltinTool tool : defaultTools(workingDirectory, skillRepository)) {
            tool.registerTo(toolRegistry);
        }
    }

    /**
     * 创建默认内置工具列表。
     */
    public static List<BuiltinTool> defaultTools(Path workingDirectory) {
        return List.of(
                new ReadTool(workingDirectory),
                new WriteTool(workingDirectory),
                new BashTool(workingDirectory),
                new EditTool(workingDirectory)
        );
    }

    /**
     * 创建带 Skill 适配能力的默认内置工具列表。
     */
    public static List<BuiltinTool> defaultTools(Path workingDirectory, SkillRepository skillRepository) {
        List<BuiltinTool> tools = new ArrayList<>(defaultTools(workingDirectory));
        if (!skillRepository.isEmpty()) {
            tools.add(new LoadSkillTool(workingDirectory, skillRepository));
        }
        return List.copyOf(tools);
    }
}
