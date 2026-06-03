package com.aster.app.extension;

import com.aster.core.event.AgentEventHandler;

import java.io.IOException;
import java.util.List;

/**
 * Aster 运行时扩展接口。
 *
 * <p>扩展只负责把能力注册到现有 Tool、Hook、EventHandler 体系里，
 * 不替代 AgentLoop、ContextPipeline 或 SessionStore 的核心职责。</p>
 */
public interface AsterRuntimeExtension {
    /**
     * 注册工具能力。
     */
    default void registerTools(RuntimeExtensionContext context) throws IOException {
    }

    /**
     * 注册 Hook 能力。
     */
    default void registerHooks(RuntimeExtensionContext context) throws IOException {
    }

    /**
     * 注册事件监听器。
     */
    default List<AgentEventHandler> eventHandlers(RuntimeExtensionContext context) throws IOException {
        return List.of();
    }
}
