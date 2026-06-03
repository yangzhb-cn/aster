package com.aster.ui.im.telegram;

import com.aster.app.runtime.AgentRuntime;
import com.aster.app.runtime.AgentRuntimeFactory;
import com.aster.app.runtime.WorkspacePaths;
import com.aster.core.session.SessionIndex;
import com.aster.core.session.model.SessionRecord;
import com.aster.ui.im.telegram.model.TelegramMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Telegram chat 的 AgentRuntime 管理器。
 *
 * <p>一个 Telegram chat 绑定一个当前 session。Runtime 按需创建，
 * 输入仍然走 AgentRuntime.submit，输出由 TelegramAgentEventHandler 监听 AgentEvent。</p>
 */
public class TelegramRuntimeManager implements AutoCloseable {
    private final AgentRuntimeFactory runtimeFactory;
    private final TelegramMessageSender sender;
    private final SessionIndex sessionIndex;
    private final TelegramSessionMapStore sessionMapStore;
    private final Map<Long, ChatRuntime> runtimes = new HashMap<>();

    public TelegramRuntimeManager(
            AgentRuntimeFactory runtimeFactory,
            TelegramMessageSender sender,
            ObjectMapper objectMapper
    ) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory);
        this.sender = Objects.requireNonNull(sender);
        this.sessionIndex = new SessionIndex(objectMapper, WorkspacePaths.SESSIONS);
        this.sessionMapStore = new TelegramSessionMapStore(objectMapper, WorkspacePaths.TELEGRAM_SESSION_MAP);
    }

    /**
     * 提交普通 Telegram 文本。
     */
    public synchronized void submit(TelegramMessage message, String text) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        chatRuntime.runtime().submit(text);
        sessionIndex.touch(chatRuntime.sessionId());
    }

    /**
     * 停止当前 chat 正在执行的 run。
     */
    public synchronized void stop(TelegramMessage message) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        boolean accepted = chatRuntime.runtime().stop();
        sender.sendMessage(message.chat().id(), accepted ? "已请求停止。" : "当前没有正在执行的任务。");
    }

    /**
     * 为当前 chat 新建一个 session 并切换过去。
     */
    public synchronized void newSession(TelegramMessage message) throws IOException {
        ChatRuntime current = runtimes.get(message.chat().id());
        if (current != null && current.runtime().isBusy()) {
            sender.sendMessage(message.chat().id(), "当前任务还在运行，结束后再新建会话。");
            return;
        }

        SessionRecord record = sessionIndex.create(TelegramSessionMapper.displayNameFor(message));
        replaceRuntime(message, record.id());
        sessionMapStore.put(message.chat().id(), record.id());
        sender.sendMessage(message.chat().id(), "已新建会话：\n" + record.displayName() + "\n" + record.id());
    }

    /**
     * 显示当前 chat 绑定的 session。
     */
    public synchronized void showSession(TelegramMessage message) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        SessionRecord record = sessionIndex.get(chatRuntime.sessionId()).orElse(null);
        String displayName = record == null ? chatRuntime.sessionId() : record.displayName();
        sender.sendMessage(message.chat().id(), "当前会话：\n" + displayName + "\n" + chatRuntime.sessionId());
    }

    private ChatRuntime runtimeFor(TelegramMessage message) throws IOException {
        long chatId = message.chat().id();
        ChatRuntime existing = runtimes.get(chatId);
        if (existing != null) {
            return existing;
        }

        String sessionId = sessionMapStore.get(chatId).orElse(TelegramSessionMapper.sessionIdFor(chatId));
        sessionIndex.ensure(sessionId, TelegramSessionMapper.displayNameFor(message));
        sessionMapStore.put(chatId, sessionId);
        return replaceRuntime(message, sessionId);
    }

    private ChatRuntime replaceRuntime(TelegramMessage message, String sessionId) throws IOException {
        long chatId = message.chat().id();
        ChatRuntime old = runtimes.remove(chatId);
        if (old != null) {
            old.runtime().close();
        }

        AgentRuntime runtime = runtimeFactory.create(
                new TelegramAgentEventHandler(sender, chatId),
                new TelegramNotificationSink(sender, chatId),
                sessionId
        );
        ChatRuntime next = new ChatRuntime(sessionId, runtime);
        runtimes.put(chatId, next);
        return next;
    }

    /**
     * 关闭所有 Telegram chat 的 runtime。
     */
    @Override
    public synchronized void close() {
        for (ChatRuntime runtime : runtimes.values()) {
            runtime.runtime().close();
        }
        runtimes.clear();
    }

    private record ChatRuntime(String sessionId, AgentRuntime runtime) {
    }
}
