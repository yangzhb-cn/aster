package dev.agentmvp.memory;

import dev.agentmvp.agent.AgentEventHandler;
import dev.agentmvp.agent.model.AgentEvent;
import dev.agentmvp.agent.model.AgentEventEnvelope;
import dev.agentmvp.background.BackgroundTaskManager;
import dev.agentmvp.background.model.BackgroundTask;
import dev.agentmvp.background.model.TaskAction;
import dev.agentmvp.background.model.TaskTrigger;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 主对话结束后的长期记忆任务调度器。
 *
 * <p>它是 AgentEventHandler，而不是 AgentLoop 的直接依赖。
 * 这样长期记忆抽取属于事件流的一个消费者，主循环仍然只负责完成当前用户请求。</p>
 */
public class MemoryExtractionSchedulingHandler implements AgentEventHandler {
    private final BackgroundTaskManager backgroundTaskManager;

    public MemoryExtractionSchedulingHandler(BackgroundTaskManager backgroundTaskManager) {
        this.backgroundTaskManager = Objects.requireNonNull(backgroundTaskManager);
    }

    @Override
    public void onEvent(AgentEventEnvelope envelope) {
        if (!(envelope.event() instanceof AgentEvent.RunFinished)) {
            return;
        }

        try {
            backgroundTaskManager.create(BackgroundTask.create(
                    "长期记忆抽取",
                    TaskTrigger.immediate(),
                    new TaskAction(
                            MemoryExtractionTaskHandler.ACTION_TYPE,
                            Map.of(
                                    "sessionName", envelope.meta().sessionName(),
                                    "runId", envelope.meta().runId()
                            )
                    )
            ));
        } catch (IOException ignored) {
            // 后台任务提交失败不能影响主对话完成。
        }
    }
}
