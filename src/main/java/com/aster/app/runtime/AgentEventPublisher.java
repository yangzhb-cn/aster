package com.aster.app.runtime;

import com.aster.core.event.AgentEventBus;
import com.aster.core.event.model.AgentEvent;

import java.util.Objects;

/**
 * 运行时事件发布桥。
 *
 * <p>有些 app 层能力在 AgentEventBus 创建之后才真正运行。
 * 这个桥在装配阶段先创建，等 EventBus 准备好后再 attach，避免工具或 Team 逻辑直接参与 UI 装配。</p>
 */
public class AgentEventPublisher {
    private AgentEventBus eventBus;

    /**
     * 绑定当前运行时的事件总线。
     */
    public synchronized void attach(AgentEventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * 开始一个新的可观察 run。
     */
    public synchronized void beginRun() {
        if (eventBus != null) {
            eventBus.beginRun();
        }
    }

    /**
     * 发布一条 Agent 事件。
     */
    public synchronized void publish(AgentEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }
}
