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
     * 启动当前 chat 的 Agent Team 探索。
     */
    public synchronized void submitTeam(TelegramMessage message, String task) throws IOException {
        if (task == null || task.isBlank()) {
            sender.sendMessage(message.chat().id(), "用法：/team 要探索的问题");
            return;
        }
        ChatRuntime chatRuntime = runtimeFor(message);
        chatRuntime.runtime().submitTeam(task);
        sessionIndex.touch(chatRuntime.sessionId());
        sender.sendMessage(message.chat().id(), "Agent Team 探索已启动：" + task);
    }

    /**
     * 为当前 chat 生成动态 Plan。
     */
    public synchronized void submitPlan(TelegramMessage message, String task) throws IOException {
        if (task == null || task.isBlank()) {
            sender.sendMessage(message.chat().id(), "用法：/plan 要完成的任务");
            return;
        }
        ChatRuntime chatRuntime = runtimeFor(message);
        chatRuntime.runtime().submitPlan(task);
        sessionIndex.touch(chatRuntime.sessionId());
        sender.sendMessage(message.chat().id(), "Plan 生成中：" + task);
    }

    /**
     * 执行当前 chat 待确认的 Plan。
     */
    public synchronized boolean startPlan(TelegramMessage message) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        boolean started = chatRuntime.runtime().startPlan();
        if (started) {
            sessionIndex.touch(chatRuntime.sessionId());
            sender.sendMessage(message.chat().id(), "Plan 已开始执行。");
        }
        return started;
    }

    /**
     * 取消当前 chat 的 Plan。
     */
    public synchronized void cancelPlan(TelegramMessage message) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        boolean canceled = chatRuntime.runtime().cancelPlan();
        sender.sendMessage(message.chat().id(), canceled ? "Plan 已取消。" : "当前没有可取消的 Plan。");
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
     * 批准当前 chat 的待审批工具调用。
     */
    public synchronized void approve(TelegramMessage message, String approvalId) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        if (approvalId == null || approvalId.isBlank()) {
            int count = chatRuntime.runtime().approveAllTools();
            sender.sendMessage(message.chat().id(), count > 0 ? "已批准全部待审批工具：" + count : "当前没有待审批工具。");
            return;
        }
        boolean accepted = chatRuntime.runtime().approveTool(approvalId.trim());
        sender.sendMessage(message.chat().id(), accepted ? "已批准工具：" + approvalId.trim() : "未找到待审批工具：" + approvalId.trim());
    }

    /**
     * 拒绝当前 chat 的待审批工具调用。
     */
    public synchronized void deny(TelegramMessage message, String approvalId, String reason) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        if (approvalId == null || approvalId.isBlank()) {
            int count = chatRuntime.runtime().denyAllTools("用户拒绝全部待审批工具");
            sender.sendMessage(message.chat().id(), count > 0 ? "已拒绝全部待审批工具：" + count : "当前没有待审批工具。");
            return;
        }
        boolean accepted = chatRuntime.runtime().denyTool(approvalId.trim(), reason);
        sender.sendMessage(message.chat().id(), accepted ? "已拒绝工具：" + approvalId.trim() : "未找到待审批工具：" + approvalId.trim());
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

    /**
     * 显示或切换当前 chat 的 Chat 模型。
     */
    public synchronized void switchModel(TelegramMessage message, String model) throws IOException {
        ChatRuntime chatRuntime = runtimeFor(message);
        AgentRuntime runtime = chatRuntime.runtime();
        if (model == null || model.isBlank()) {
            sender.sendMessage(message.chat().id(), "当前模型：" + runtime.chatModel()
                    + "\n可选模型：" + String.join(", ", runtime.availableChatModels()));
            return;
        }

        try {
            String selected = runtime.switchChatModel(model.trim());
            sender.sendMessage(message.chat().id(), "已切换模型：" + selected);
        } catch (IllegalArgumentException error) {
            sender.sendMessage(message.chat().id(), error.getMessage());
        }
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
