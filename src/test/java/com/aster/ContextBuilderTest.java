package com.aster;

import com.aster.llm.text.model.Message;
import com.aster.llm.text.model.ToolCall;
import com.aster.core.context.model.ContextBuildResult;
import com.aster.core.context.ContextBuilder;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.context.SimpleTokenEstimator;
import com.aster.core.context.ToolProtocolValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContextBuilder 的压缩边界测试。
 *
 * <p>重点验证旧轮次被摘要替换后，不会留下半截 tool_call 或工具结果。</p>
 */
class ContextBuilderTest {
    /**
     * 验证旧轮次被压缩成摘要字段，最近已完成轮次和当前轮次原样保留。
     */
    @Test
    void compressesOldTurnsIntoSummaryFieldAndKeepsRecentTurns() {
        ContextBuilder builder = new ContextBuilder(
                new SimpleTokenEstimator(),
                ignored -> "old turns were summarized",
                new ContextOptions(10, 0.1, 1)
        );

        List<Message> history = List.of(
                Message.system("You are a coding agent."),
                Message.user("Read config and explain the issue."),
                Message.assistantToolCalls(List.of(
                        ToolCall.function("call_old", "read_file", "{\"path\":\"config.yml\"}")
                )),
                Message.tool("call_old", "database.url is missing"),
                Message.assistant("The config is missing database.url."),
                Message.user("Review the proposed fix."),
                Message.assistant("The fix should update database.url."),
                Message.user("Now fix it."),
                Message.assistant("I will update the config.")
        );

        ContextBuildResult result = builder.build(history);

        assertTrue(result.compressed());
        assertEquals("old turns were summarized", result.summary());
        assertFalse(result.messages().stream().anyMatch(message ->
                message.content() != null && message.content().contains("old turns were summarized")
        ));
        assertTrue(result.messages().stream().anyMatch(message ->
                "user".equals(message.role()) && message.content().contains("Review the proposed fix.")
        ));
        assertTrue(result.messages().stream().anyMatch(message ->
                "user".equals(message.role()) && message.content().contains("Now fix it.")
        ));

        // 旧工具协议已经被摘要吃掉，不能残留过期的 tool_call/tool_call_id。
        assertFalse(result.messages().stream().anyMatch(message -> "call_old".equals(message.toolCallId())));
        assertFalse(result.messages().stream()
                .flatMap(message -> message.toolCalls().stream())
                .anyMatch(call -> "call_old".equals(call.id())));

        ToolProtocolValidator.validate(result.messages());
    }
}
