package com.aster.core.tool;

import com.aster.llm.text.model.ToolCall;
import com.aster.core.tool.model.ToolResult;

import java.util.Map;

/**
 * 一个本地 Java 工具背后的具体处理函数。
 */
@FunctionalInterface
public interface ToolHandler {
    /**
     * 处理已经解析好的 JSON 参数。
     */
    ToolResult handle(ToolCall call, Map<String, Object> arguments) throws Exception;
}
