package dev.agentmvp.app.memory;

import dev.agentmvp.app.background.BackgroundTaskHandler;
import dev.agentmvp.app.background.model.BackgroundTask;
import dev.agentmvp.app.background.model.TaskAction;

import java.util.Objects;

/**
 * 后台长期记忆抽取任务处理器。
 *
 * <p>它接收 action.type=memory_extract 的后台任务，
 * 然后调用 MemoryExtractionAgent。调度、记录和通知仍由 background 包负责。</p>
 */
public class MemoryExtractionTaskHandler implements BackgroundTaskHandler {
    public static final String ACTION_TYPE = "memory_extract";

    private final MemoryExtractionAgent memoryExtractionAgent;

    public MemoryExtractionTaskHandler(MemoryExtractionAgent memoryExtractionAgent) {
        this.memoryExtractionAgent = Objects.requireNonNull(memoryExtractionAgent);
    }

    @Override
    public boolean supports(TaskAction action) {
        return action != null && ACTION_TYPE.equals(action.type());
    }

    @Override
    public String handle(BackgroundTask task) throws Exception {
        return memoryExtractionAgent.extract();
    }
}
