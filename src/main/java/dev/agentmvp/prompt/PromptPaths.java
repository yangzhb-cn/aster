package dev.agentmvp.prompt;

/**
 * 内置 prompt 资源路径。
 *
 * <p>这些路径是 classpath 路径，不是源码目录路径。
 * 也就是说运行 jar 时读取的是 jar 里的 prompts/*.md。</p>
 */
public final class PromptPaths {
    public static final String SYSTEM = "/prompts/system.md";
    public static final String CONTEXT_SUMMARY = "/prompts/context-summary.md";
    public static final String LONG_TERM_MEMORY_SYSTEM = "/prompts/long-term-memory-system.md";
    public static final String MEMORY_EXTRACTION = "/prompts/memory-extraction.md";

    private PromptPaths() {
    }
}
