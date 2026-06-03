package com.aster.app.memory;

import java.util.Objects;

/**
 * 长期记忆提醒块渲染器。
 *
 * <p>模板来自 resources/prompts/long-term-memory-system.md。
 * Java 只负责把 Markdown 记忆替换进去，避免把 XML 提醒模板硬编码在代码里。</p>
 */
public class MemoryPromptRenderer {
    private final String template;

    public MemoryPromptRenderer(String template) {
        this.template = Objects.requireNonNull(template);
    }

    /**
     * 把长期记忆 Markdown 包装成一个临时提醒块。
     */
    public String render(String memoryMarkdown) {
        if (memoryMarkdown == null || memoryMarkdown.isBlank()) {
            return "";
        }
        return template.replace("{{memory}}", memoryMarkdown.strip());
    }
}
