package com.aster.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI 兼容协议里的聊天消息。
 *
 * <p>只有 assistant 消息可以带 {@code tool_calls}；
 * 只有 tool 消息可以带 {@code tool_call_id}。
 * 字段放在正确角色上，才能避免工具调用协议被破坏。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Message(
        String role,
        String content,
        @JsonProperty("reasoning_content") String reasoningContent,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {
    public Message {
        if (content != null && content.isBlank()) {
            content = null;
        }
        if (reasoningContent != null && reasoningContent.isBlank()) {
            reasoningContent = null;
        }
        // 空数组对很多 chat API 没有意义，这里统一归一化，避免序列化出多余字段。
        if (toolCalls == null) {
            toolCalls = List.of();
        }
        if ("assistant".equals(role) && content == null && toolCalls.isEmpty() && reasoningContent != null) {
            // DeepSeek thinking mode 偶尔可能只返回 reasoning_content，没有可见 content。
            // 但 OpenAI-compatible 请求历史里 assistant 必须有 content 或 tool_calls。
            // 教学版把这类 reasoning-only 回复降级成可见 content，避免恢复历史后请求 400。
            content = reasoningContent;
            reasoningContent = null;
        }
    }

    /**
     * 创建 system 指令消息。
     */
    public static Message system(String content) {
        return new Message("system", content, null, List.of(), null);
    }

    /**
     * 创建 user 消息。上下文摘要也走这里，确保摘要消息不携带工具协议字段。
     */
    public static Message user(String content) {
        return new Message("user", content, null, List.of(), null);
    }

    /**
     * 创建普通 assistant 文本回复。
     */
    public static Message assistant(String content) {
        return assistant(content, null);
    }

    /**
     * 创建带 DeepSeek thinking 内容的 assistant 文本回复。
     *
     * <p>{@code reasoning_content} 是 DeepSeek thinking mode 在响应里返回的字段。
     * 如果本轮没有工具调用，后续请求可以不依赖它；但保存下来有利于 TUI 展示。</p>
     */
    public static Message assistant(String content, String reasoningContent) {
        return new Message("assistant", content, reasoningContent, List.of(), null);
    }

    /**
     * 创建请求宿主程序执行工具的 assistant 消息。
     */
    public static Message assistantToolCalls(List<ToolCall> toolCalls) {
        return assistantToolCalls(null, null, toolCalls);
    }

    /**
     * 创建带 thinking 内容的工具调用 assistant 消息。
     *
     * <p>DeepSeek V4 thinking mode 下，如果 assistant 这一轮发起了工具调用，
     * 后续请求必须把这条 assistant 消息里的 {@code reasoning_content} 原样带回 API。
     * 否则 DeepSeek 会认为上一轮推理上下文丢失，可能直接返回 400。</p>
     *
     * <p>TODO：后续评估是否在进入下一轮用户请求或压缩旧 turn 前，清理已完成历史轮次里的
     * {@code reasoning_content}。当前轮工具调用链路不能清理，否则可能破坏 DeepSeek 协议。</p>
     */
    public static Message assistantToolCalls(String content, String reasoningContent, List<ToolCall> toolCalls) {
        // 工具调用型 assistant 消息通常没有正文，只通过 tool_calls 表达动作。
        return new Message("assistant", content, reasoningContent, toolCalls, null);
    }

    /**
     * 创建和某个 assistant 工具调用 id 配对的 tool 结果消息。
     */
    public static Message tool(String toolCallId, String content) {
        return new Message("tool", content, null, List.of(), toolCallId);
    }

    /**
     * 判断这条 assistant 消息是否请求了一个或多个工具调用。
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
