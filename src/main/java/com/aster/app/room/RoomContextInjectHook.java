package com.aster.app.room;

import com.aster.core.hook.BeforeLlmRequestContext;
import com.aster.core.hook.HookHandler;
import com.aster.llm.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 房间上下文注入 Hook。
 *
 * <p>它在发送 LLM 前，把共享房间消息包装成 {@code <system-reminder>}，
 * 临时拼到最后一条 user 消息开头。这样 Agent 私有 session 只保存自己的对话，
 * 房间消息不会永久混入私有上下文。</p>
 */
public class RoomContextInjectHook implements HookHandler<BeforeLlmRequestContext, BeforeLlmRequestContext> {
    private final RoomAgentRunContext runContext;
    private final RoomPromptBuilder promptBuilder;

    public RoomContextInjectHook(RoomAgentRunContext runContext, RoomPromptBuilder promptBuilder) {
        this.runContext = Objects.requireNonNull(runContext);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
    }

    /**
     * 在请求前注入房间共享消息。
     */
    @Override
    public BeforeLlmRequestContext handle(BeforeLlmRequestContext context) {
        int userIndex = lastUserMessageIndex(context.messages());
        if (userIndex < 0) {
            return context;
        }

        List<Message> messages = new ArrayList<>(context.messages());
        Message user = messages.get(userIndex);
        String content = user.content() == null ? "" : user.content();
        messages.set(userIndex, Message.user(promptBuilder.roomReminder(runContext) + "\n\n" + content));
        return context.withMessages(messages);
    }

    private int lastUserMessageIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                return i;
            }
        }
        return -1;
    }
}
