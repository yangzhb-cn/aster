package dev.agentmvp.runtime;

import dev.agentmvp.agent.AgentLoop;
import dev.agentmvp.llm.model.OpenAiCompatibleProvider;
import dev.agentmvp.tool.ParallelToolExecutor;

import java.io.IOException;
import java.util.Objects;

/**
 * Agent 运行时对象。
 *
 * <p>它把 AgentLoop 和需要关闭的执行器放在一起。
 * 这样 TUI、Web 或未来其他入口不需要知道底层有多少组件需要释放。</p>
 */
public class AgentRuntime implements AutoCloseable {
    private final AgentLoop agentLoop;
    private final ParallelToolExecutor parallelToolExecutor;
    private final OpenAiCompatibleProvider provider;
    private final int skillCount;

    public AgentRuntime(
            AgentLoop agentLoop,
            ParallelToolExecutor parallelToolExecutor,
            OpenAiCompatibleProvider provider,
            int skillCount
    ) {
        this.agentLoop = Objects.requireNonNull(agentLoop);
        this.parallelToolExecutor = Objects.requireNonNull(parallelToolExecutor);
        this.provider = Objects.requireNonNull(provider);
        this.skillCount = skillCount;
    }

    /**
     * 执行一次用户请求。
     */
    public String run(String userInput) throws IOException {
        return agentLoop.run(userInput);
    }

    /**
     * 当前模型供应商信息。
     */
    public OpenAiCompatibleProvider provider() {
        return provider;
    }

    /**
     * 启动时扫描到的 Skill 数量。
     */
    public int skillCount() {
        return skillCount;
    }

    /**
     * 关闭工具并行执行器。
     */
    @Override
    public void close() {
        parallelToolExecutor.close();
    }
}
