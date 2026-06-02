package dev.agentmvp.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 模型生成的一次函数式工具调用。
 *
 * <p>{@code id} 很关键：每个工具调用后面都必须收到一条
 * 使用同一个 {@code tool_call_id} 的 {@code role=tool} 消息。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolCall(
        String id,
        String type,
        FunctionCall function
) {
    /**
     * 创建 OpenAI 兼容协议里的 function 工具调用。
     */
    public static ToolCall function(String id, String name, String argumentsJson) {
        return new ToolCall(id, "function", new FunctionCall(name, argumentsJson));
    }

    /**
     * 模型生成的函数名和原始 JSON 参数字符串。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FunctionCall(
            String name,
            @JsonProperty("arguments") String argumentsJson
    ) {
    }
}
