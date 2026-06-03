package dev.agentmvp.app.memory;

import dev.agentmvp.app.background.BackgroundTaskManager;
import dev.agentmvp.app.background.model.BackgroundTask;
import dev.agentmvp.app.background.model.TaskAction;
import dev.agentmvp.app.background.model.TaskTrigger;
import dev.agentmvp.core.hook.AfterRunContext;
import dev.agentmvp.core.hook.HookHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 长期记忆抽取 Hook。
 *
 * <p>它挂在 afterRun 阶段：主对话已经完成，session 也已经写入，
 * 这时只提交一个 memory_extract 后台任务，不在主线程里等待抽取结果。</p>
 */
public class MemoryExtractionHook implements HookHandler<AfterRunContext, Void> {
    private final BackgroundTaskManager backgroundTaskManager;

    public MemoryExtractionHook(BackgroundTaskManager backgroundTaskManager) {
        this.backgroundTaskManager = Objects.requireNonNull(backgroundTaskManager);
    }

    @Override
    public Void handle(AfterRunContext context) throws IOException {
        backgroundTaskManager.create(BackgroundTask.create(
                "长期记忆抽取",
                TaskTrigger.immediate(),
                new TaskAction(
                        MemoryExtractionTaskHandler.ACTION_TYPE,
                        Map.of(
                                "sessionName", context.sessionName(),
                                "runId", context.runId()
                        )
                )
        ));
        return null;
    }
}
