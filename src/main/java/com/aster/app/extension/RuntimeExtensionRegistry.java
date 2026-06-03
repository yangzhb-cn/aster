package com.aster.app.extension;

import com.aster.core.event.AgentEventHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 运行时扩展注册表。
 *
 * <p>它按固定顺序应用扩展，保证默认能力的装配顺序集中可读。
 * 教学版先使用显式列表，不做 classpath 扫描或动态插件加载。</p>
 */
public class RuntimeExtensionRegistry {
    private final List<AsterRuntimeExtension> extensions;

    public RuntimeExtensionRegistry(List<AsterRuntimeExtension> extensions) {
        this.extensions = List.copyOf(Objects.requireNonNull(extensions));
    }

    /**
     * 创建 Aster 默认运行时扩展列表。
     */
    public static RuntimeExtensionRegistry defaults() {
        return new RuntimeExtensionRegistry(List.of(
                new SkillToolExtension(),
                new McpToolExtension(),
                new SteerExtension(),
                new SystemReminderExtension(),
                new MemoryExtension(),
                new ToolResultExtension()
        ));
    }

    /**
     * 按顺序注册所有扩展工具。
     */
    public void registerTools(RuntimeExtensionContext context) throws IOException {
        for (AsterRuntimeExtension extension : extensions) {
            extension.registerTools(context);
        }
    }

    /**
     * 按顺序注册所有扩展 Hook。
     */
    public void registerHooks(RuntimeExtensionContext context) throws IOException {
        for (AsterRuntimeExtension extension : extensions) {
            extension.registerHooks(context);
        }
    }

    /**
     * 收集所有扩展事件监听器。
     */
    public List<AgentEventHandler> eventHandlers(RuntimeExtensionContext context) throws IOException {
        List<AgentEventHandler> handlers = new ArrayList<>();
        for (AsterRuntimeExtension extension : extensions) {
            handlers.addAll(extension.eventHandlers(context));
        }
        return List.copyOf(handlers);
    }
}
