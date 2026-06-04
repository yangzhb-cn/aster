package com.aster.ui.web;

import com.aster.app.notification.NotificationSink;
import com.aster.app.runtime.AgentRuntime;
import com.aster.app.runtime.AgentRuntimeFactory;
import com.aster.core.event.AgentEventHandler;
import com.aster.core.session.SessionCatalog;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Web session 运行时池。
 *
 * <p>每个聊天 session 对应一个独立的 AgentRuntime。这样用户切换到 B 时，
 * A 的 AgentRuntime 不会被关闭，A/B 可以并行运行且各自写入自己的 JSONL。</p>
 */
public class WebSessionRuntimePool implements AutoCloseable {
    private final AgentRuntimeFactory runtimeFactory;
    private final AgentEventHandler eventHandler;
    private final NotificationSink notificationSink;
    private final Map<String, AgentRuntime> runtimes = new LinkedHashMap<>();

    public WebSessionRuntimePool(
            AgentRuntimeFactory runtimeFactory,
            AgentEventHandler eventHandler,
            NotificationSink notificationSink
    ) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory);
        this.eventHandler = Objects.requireNonNull(eventHandler);
        this.notificationSink = Objects.requireNonNull(notificationSink);
    }

    /**
     * 获取指定 session 的运行时；不存在时按需创建。
     */
    public synchronized AgentRuntime runtimeFor(String sessionId) throws IOException {
        SessionCatalog.requireValidName(sessionId);
        AgentRuntime existing = runtimes.get(sessionId);
        if (existing != null) {
            return existing;
        }

        AgentRuntime created = runtimeFactory.create(eventHandler, notificationSink, sessionId);
        runtimes.put(sessionId, created);
        return created;
    }

    /**
     * 返回已经创建的 session 运行时。
     */
    public synchronized Optional<AgentRuntime> existing(String sessionId) {
        return Optional.ofNullable(runtimes.get(sessionId));
    }

    /**
     * 如果指定 session 空闲，则关闭并移出运行时池。
     */
    public synchronized void closeIfIdle(String sessionId) throws IOException {
        AgentRuntime runtime = runtimes.get(sessionId);
        if (runtime == null) {
            return;
        }
        if (runtime.isBusy()) {
            throw new IOException("agent is running, wait for current request to finish");
        }
        runtimes.remove(sessionId);
        runtime.close();
    }

    /**
     * 指定 session 是否正在运行。
     */
    public synchronized boolean isBusy(String sessionId) {
        AgentRuntime runtime = runtimes.get(sessionId);
        return runtime != null && runtime.isBusy();
    }

    /**
     * 汇总一组 session 的运行状态。
     */
    public synchronized Map<String, WebSessionRuntimeStatus> statuses(Collection<String> sessionIds) {
        Map<String, WebSessionRuntimeStatus> result = new LinkedHashMap<>();
        for (String sessionId : sessionIds) {
            result.put(sessionId, statusOf(sessionId));
        }
        return result;
    }

    /**
     * 返回单个 session 的运行状态。
     */
    public synchronized WebSessionRuntimeStatus statusOf(String sessionId) {
        AgentRuntime runtime = runtimes.get(sessionId);
        if (runtime == null) {
            return new WebSessionRuntimeStatus(sessionId, false, false, false, 0, 0);
        }
        return new WebSessionRuntimeStatus(
                sessionId,
                true,
                runtime.isBusy(),
                runtime.hasPendingPlan(),
                runtime.queuedCount(),
                runtime.pendingToolApprovals().size()
        );
    }

    /**
     * 关闭所有 session 运行时。
     */
    @Override
    public synchronized void close() {
        for (AgentRuntime runtime : runtimes.values()) {
            runtime.close();
        }
        runtimes.clear();
    }
}
