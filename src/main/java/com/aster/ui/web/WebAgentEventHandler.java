package com.aster.ui.web;

import com.aster.core.event.AgentEventHandler;
import com.aster.core.event.model.AgentEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 把 Agent 事件转成 Web SSE。
 *
 * <p>它不渲染 HTML，也不管理 Agent 执行，只负责把统一事件信封广播给浏览器。</p>
 */
public class WebAgentEventHandler implements AgentEventHandler {
    private final ObjectMapper objectMapper;
    private final WebSseClientRegistry clients;

    public WebAgentEventHandler(ObjectMapper objectMapper, WebSseClientRegistry clients) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clients = Objects.requireNonNull(clients);
    }

    /**
     * 处理一次 Agent 事件并广播。
     */
    @Override
    public void onEvent(AgentEventEnvelope envelope) {
        try {
            clients.broadcast("agent", objectMapper.writeValueAsString(WebAgentEventMapper.toMap(envelope)));
        } catch (JsonProcessingException e) {
            clients.broadcast("agent", "{\"type\":\"WebSerializationFailed\"}");
        }
    }
}
