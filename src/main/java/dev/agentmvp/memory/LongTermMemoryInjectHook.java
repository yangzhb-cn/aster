package dev.agentmvp.memory;

import dev.agentmvp.hook.BeforeLlmRequestContext;
import dev.agentmvp.hook.HookHandler;
import dev.agentmvp.llm.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 长期记忆注入 Hook。
 *
 * <p>它挂在 beforeLlmRequest 阶段，每轮请求前读取 Markdown 长期记忆，
 * 渲染成 system message，并插入到现有 system 消息之后。</p>
 */
public class LongTermMemoryInjectHook implements HookHandler<BeforeLlmRequestContext, BeforeLlmRequestContext> {
    private final MarkdownMemoryStore memoryStore;
    private final MemoryPromptRenderer memoryPromptRenderer;

    public LongTermMemoryInjectHook(MarkdownMemoryStore memoryStore, MemoryPromptRenderer memoryPromptRenderer) {
        this.memoryStore = Objects.requireNonNull(memoryStore);
        this.memoryPromptRenderer = Objects.requireNonNull(memoryPromptRenderer);
    }

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
        int insertAt = 0;
        while (insertAt < messages.size() && "system".equals(messages.get(insertAt).role())) {
            insertAt++;
        }

        List<Message> result = new ArrayList<>(messages.size() + 1);
        result.addAll(messages.subList(0, insertAt));
        result.add(Message.system(memoryPrompt));
        result.addAll(messages.subList(insertAt, messages.size()));
        return context.withMessages(result);
    }
}
