package dev.agentmvp.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.agentmvp.llm.model.Message;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 chat/completions 请求体。
 *
 * <p>这个 MVP 只走流式，所以 stream 应该传 true。tools 是已经转换好的
 * OpenAI 工具结构，messages 是 ContextBuilder 压缩后的最终上下文。</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatRequest(
        String model,
        List<Message> messages,
        List<Map<String, Object>> tools,
        @JsonProperty("tool_choice") String toolChoice,
        Boolean stream,
        Map<String, Object> thinking,
        @JsonProperty("reasoning_effort") String reasoningEffort,
        @JsonProperty("stream_options") Map<String, Object> streamOptions
) {
    /**
     * 构造普通 OpenAI-compatible 流式请求。
     */
    public static ChatRequest streaming(
            String model,
            List<Message> messages,
            List<Map<String, Object>> tools,
            String toolChoice
    ) {
        return streaming(model, messages, tools, toolChoice, false, null);
    }

    /**
     * 构造可选 DeepSeek thinking mode 的流式请求。
     *
     * <p>DeepSeek V4 thinking mode 需要在请求体里显式打开 {@code thinking.type=enabled}。
     * {@code reasoning_effort} 用来告诉模型这轮请求的思考强度，教学版默认由供应商配置决定。</p>
     */
    public static ChatRequest streaming(
            String model,
            List<Message> messages,
            List<Map<String, Object>> tools,
            String toolChoice,
            boolean thinkingEnabled,
            String reasoningEffort
    ) {
        return new ChatRequest(
                model,
                messages,
                tools,
                toolChoice,
                true,
                thinkingEnabled ? Map.of("type", "enabled") : null,
                thinkingEnabled ? reasoningEffort : null,
                Map.of("include_usage", true)
        );
    }
}
