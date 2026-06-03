package com.aster.app.memory;

import java.util.Objects;

/**
 * 长期记忆提醒段落渲染器。
 *
 * <p>模板来自 resources/prompts/memory/injection.md。
 * Java 只负责把 Markdown 记忆替换进去，外层 system-reminder 标签由请求前 Hook 统一生成。</p>
 */
public class MemoryPromptRenderer {
    private final String template;

    public MemoryPromptRenderer(String template) {
        this.template = Objects.requireNonNull(template);
    }

    /**
     * 把长期记忆 Markdown 包装成提醒段落。
     */
    public String render(String memoryMarkdown) {
        if (memoryMarkdown == null || memoryMarkdown.isBlank()) {
            return "";
        }
        return template.replace("{{memory}}", memoryMarkdown.strip());
    }
}
