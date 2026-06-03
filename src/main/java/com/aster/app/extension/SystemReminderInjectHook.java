package com.aster.app.extension;

import com.aster.app.memory.MarkdownMemoryStore;
import com.aster.app.memory.MemoryPromptRenderer;
import com.aster.core.hook.BeforeLlmRequestContext;
import com.aster.core.hook.HookHandler;
import com.aster.llm.model.Message;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LLM 请求前的系统提醒注入 Hook。
 *
 * <p>它把当前时间、Skill 索引、旧对话摘要和长期记忆合并成一个
 * {@code <system-reminder>} 块，临时拼到最后一条 user 消息开头。
 * 这些内容只参与本次模型请求，不写入 SessionStore。</p>
 */
public class SystemReminderInjectHook implements HookHandler<BeforeLlmRequestContext, BeforeLlmRequestContext> {
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private final String skillIndex;
    private final MarkdownMemoryStore memoryStore;
    private final MemoryPromptRenderer memoryPromptRenderer;

    public SystemReminderInjectHook(
            String skillIndex,
            MarkdownMemoryStore memoryStore,
            MemoryPromptRenderer memoryPromptRenderer
    ) {
        this.skillIndex = skillIndex == null ? "" : skillIndex.strip();
        this.memoryStore = Objects.requireNonNull(memoryStore);
        this.memoryPromptRenderer = Objects.requireNonNull(memoryPromptRenderer);
    }

    /**
     * 在发送给 LLM 前，把运行时提醒内容注入最后一条 user 消息。
     */
    @Override
    public BeforeLlmRequestContext handle(BeforeLlmRequestContext context) throws IOException {
        String reminder = renderReminder(context);
        if (reminder.isBlank()) {
            return context;
        }

        List<Message> messages = context.messages();
        int userIndex = lastUserMessageIndex(messages);
        if (userIndex < 0) {
            return context;
        }

        Message userMessage = messages.get(userIndex);
        String userContent = userMessage.content() == null ? "" : userMessage.content();
        Message injectedUserMessage = Message.user(reminder + "\n\n" + userContent);

        List<Message> result = new ArrayList<>(messages);
        result.set(userIndex, injectedUserMessage);
        return context.withMessages(result);
    }

    /**
     * 渲染统一的 system-reminder 块。
     */
    private String renderReminder(BeforeLlmRequestContext context) throws IOException {
        List<String> sections = new ArrayList<>();
        sections.add(renderRuntimeInfo());
        if (!skillIndex.isBlank()) {
            sections.add(skillIndex);
        }
        if (context.contextSummary() != null && !context.contextSummary().isBlank()) {
            sections.add("""
                    ## 旧对话摘要

                    以下是系统自动生成的旧对话压缩摘要，不是用户的新请求：

                    %s
                    """.formatted(context.contextSummary().strip()).strip());
        }

        String memoryMarkdown = memoryStore.load();
        if (memoryStore.hasMemoryContent(memoryMarkdown)) {
            String memoryPrompt = memoryPromptRenderer.render(memoryMarkdown);
            if (!memoryPrompt.isBlank()) {
                sections.add(memoryPrompt);
            }
        }

        if (sections.isEmpty()) {
            return "";
        }
        return "<system-reminder>\n" + String.join("\n\n", sections) + "\n</system-reminder>";
    }

    /**
     * 渲染每轮请求都需要的动态时间信息。
     */
    private String renderRuntimeInfo() {
        ZonedDateTime now = ZonedDateTime.now();
        return """
                ## 当前运行信息

                以下是宿主程序发送本轮请求前注入的动态信息，不是用户的新请求：

                - 当前日期：%s
                - 当前时间：%s
                - 当前时区：%s
                - 当前 UTC 时间：%s

                解释“今天”“明天”“昨天”“几分钟后”等相对时间时，以这里为准。
                """.formatted(
                now.toLocalDate(),
                LOCAL_TIME_FORMATTER.format(now),
                now.getZone(),
                now.toInstant()
        ).strip();
    }

    /**
     * 找到当前请求里最后一条 user 消息，避免把提醒块注入 system、assistant 或 tool 消息。
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
