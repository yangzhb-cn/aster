package com.aster;

import com.aster.core.event.model.AgentEvent;
import com.aster.core.event.model.AgentEventEnvelope;
import com.aster.core.event.model.AgentEventMeta;
import com.aster.ui.web.WebAgentEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web 事件 DTO 测试。
 */
class WebAgentEventMapperTest {
    /**
     * 验证 Web 事件包含类型、字符串时间戳和 payload，且可以直接 JSON 序列化。
     */
    @Test
    void mapsAgentEventEnvelopeToSerializableWebEvent() throws Exception {
        AgentEventEnvelope envelope = new AgentEventEnvelope(
                new AgentEventMeta("event-1", "run-1", "default", 7, Instant.parse("2026-06-04T00:00:00Z")),
                new AgentEvent.AssistantToken("hello")
        );

        Map<String, Object> dto = WebAgentEventMapper.toMap(envelope);
        String json = new ObjectMapper().writeValueAsString(dto);

        assertEquals("AssistantToken", dto.get("type"));
        assertTrue(json.contains("\"timestamp\":\"2026-06-04T00:00:00Z\""));
        assertTrue(json.contains("\"text\":\"hello\""));
    }
}
