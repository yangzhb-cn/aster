package com.aster.app.memory;

import com.aster.core.hook.BeforeLlmRequestContext;
import com.aster.core.hook.HookHandler;
import com.aster.llm.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 长期记忆注入 Hook。
 *
 * <p>它挂在 beforeLlmRequest 阶段，每轮请求前读取 Markdown 长期记忆，
 * 渲染成临时提醒块，并注入最后一条 user 消息开头。
 * 这个提醒块只进入本次 LLM 请求，不写入 SessionStore。</p>
 */
public class LongTermMemoryInjectHook implements HookHandler<BeforeLlmRequestContext, BeforeLlmRequestContext> {
    private final MarkdownMemoryStore memoryStore;
    private final MemoryPromptRenderer memoryPromptRenderer;

    public LongTermMemoryInjectHook(MarkdownMemoryStore memoryStore, MemoryPromptRenderer memoryPromptRenderer) {
        this.memoryStore = Objects.requireNonNull(memoryStore);
        this.memoryPromptRenderer = Objects.requireNonNull(memoryPromptRenderer);
    }

    /**
     * 把长期记忆作为临时 XML 提醒块拼到最后一条 user 消息开头。
     */
    @Override
    public BeforeLlmRequestContext handle(BeforeLlmRequestContext context) throws IOException {
        String memoryMarkdown = memoryStore.load();
        if (!memoryStore.hasMemoryContent(memoryMarkdown)) {
            return context;
        }

        String memoryPrompt = memoryPromptRenderer.render(memoryMarkdown);
        if (memoryPrompt.isBlank()) {
            return context;
        }

        List<Message> messages = context.messages();
        int userIndex = lastUserMessageIndex(messages);
        if (userIndex < 0) {
            return context;
        }

        Message userMessage = messages.get(userIndex);
        String userContent = userMessage.content() == null ? "" : userMessage.content();
        Message injectedUserMessage = Message.user(memoryPrompt + "\n\n" + userContent);

        List<Message> result = new ArrayList<>(messages);
        result.set(userIndex, injectedUserMessage);
        return context.withMessages(result);
    }

    /**
     * 找到当前请求里最后一条 user 消息，避免把长期记忆注入历史摘要或工具结果。
     */
    private int lastUserMessageIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                return i;
            }
        }
        return -1;
    }
}
