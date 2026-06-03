package com.aster.app.tool.developer;

import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;

import java.util.Map;

/**
 * 开发者扩展工具接口。
 *
 * <p>这批工具不属于 read/write/bash/edit 四个基础内置工具，
 * 只通过 RuntimeExtension 注册到当前运行时。</p>
 */
public interface DeveloperTool {
    /**
     * 返回模型可见的工具定义。
     */
    Tool definition();

    /**
     * 执行工具逻辑。
     */
    ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception;

    /**
     * 把扩展工具注册到当前 ToolRegistry。
     */
    default void registerTo(ToolRegistry toolRegistry) {
        toolRegistry.registerLocal(definition(), this::execute);
    }
}
