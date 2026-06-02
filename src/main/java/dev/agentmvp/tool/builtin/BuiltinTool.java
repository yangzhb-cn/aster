package dev.agentmvp.tool.builtin;

import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.tool.ToolRegistry;
import dev.agentmvp.tool.model.Tool;
import dev.agentmvp.tool.model.ToolResult;

import java.util.Map;

/**
 * 单个内置工具的统一接口。
 *
 * <p>每个工具一个类，实现这个接口即可。这样以后新增工具时，不需要修改一个大工具类，
 * 只要新增类并注册进去。</p>
 */
public interface BuiltinTool {
    /**
     * 返回模型可见的工具定义。
     */
    Tool definition();

    /**
     * 执行工具逻辑。
     */
    ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception;

    /**
     * 把工具同时注册到工具定义表和本地执行器。
     */
    default void registerTo(ToolRegistry toolRegistry) {
        toolRegistry.registerLocal(definition(), this::execute);
    }
}
