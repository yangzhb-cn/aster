package com.aster.app.tool.builtin;

import com.aster.core.tool.ToolRegistry;

import java.nio.file.Path;
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

}
