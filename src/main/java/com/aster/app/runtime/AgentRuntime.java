package com.aster.app.runtime;

import com.aster.core.agent.AgentLoop;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.aster.app.mcp.McpToolExecutor;
import com.aster.core.tool.ParallelToolExecutor;

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
    private final AgentRunCoordinator runCoordinator;
    private final BackgroundTaskManager backgroundTaskManager;
    private final ParallelToolExecutor parallelToolExecutor;
    private final McpToolExecutor mcpToolExecutor;
    private final OpenAiCompatibleProvider provider;
    private final String sessionName;
    private final int skillCount;

    public AgentRuntime(
            AgentLoop agentLoop,
            AgentRunCoordinator runCoordinator,
            BackgroundTaskManager backgroundTaskManager,
            ParallelToolExecutor parallelToolExecutor,
            McpToolExecutor mcpToolExecutor,
            OpenAiCompatibleProvider provider,
            String sessionName,
            int skillCount
    ) {
        this.agentLoop = Objects.requireNonNull(agentLoop);
        this.runCoordinator = Objects.requireNonNull(runCoordinator);
        this.backgroundTaskManager = Objects.requireNonNull(backgroundTaskManager);
        this.parallelToolExecutor = Objects.requireNonNull(parallelToolExecutor);
        this.mcpToolExecutor = Objects.requireNonNull(mcpToolExecutor);
        this.provider = Objects.requireNonNull(provider);
        this.sessionName = Objects.requireNonNull(sessionName);
        this.skillCount = skillCount;
    }

    /**
     * 执行一次用户请求。
     */
    public String run(String userInput) throws IOException {
        return agentLoop.run(userInput);
    }

    /**
     * 提交用户输入。运行中提交会进入 follow-up 队列。
     */
    public void submit(String userInput) {
        runCoordinator.submit(userInput);
    }

    /**
     * 向当前 run 发送运行中引导。
     */
    public boolean steer(String text) {
        return runCoordinator.steer(text);
    }

    /**
     * 请求当前 run 停止。
     */
    public boolean stop() {
        return runCoordinator.stop();
    }

    /**
     * 判断当前是否有正在执行或等待执行的输入。
     */
    public boolean isBusy() {
        return runCoordinator.isBusy();
    }

    /**
     * 返回 follow-up 队列长度。
     */
    public int queuedCount() {
        return runCoordinator.queuedCount();
    }

    /**
     * 后台任务管理器。
     */
    public BackgroundTaskManager backgroundTaskManager() {
        return backgroundTaskManager;
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
     * 当前绑定的 session 名称。
     */
    public String sessionName() {
        return sessionName;
    }

    /**
     * 关闭运行时资源。
     */
    @Override
    public void close() {
        runCoordinator.close();
        backgroundTaskManager.close();
        parallelToolExecutor.close();
        mcpToolExecutor.close();
    }
}
