package com.aster.llm.text.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 流式 tool_call 的增量片段。
 *
 * <p>模型可能先返回 tool_call id，再分多次返回函数名和参数。
 * 所以这里保存的是增量，不是最终完整 ToolCall。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallDelta(
        int index,
        String id,
        String type,
        FunctionDelta function
) {
    /**
     * 函数调用字段的局部增量。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionDelta(
            String name,
            @JsonProperty("arguments") String argumentsJson
    ) {
    }
}
