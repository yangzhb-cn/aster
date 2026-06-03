package dev.agentmvp.core.hook;

/**
 * 一次用户请求完成后的 Hook 上下文。
 *
 * <p>长期记忆抽取、审计日志、后台总结等动作适合挂在这里。
 * 这些动作不应该阻塞主回答返回。</p>
 */
public record AfterRunContext(
        String sessionName,
        String runId,
        String userInput,
        String finalText
) {
}
