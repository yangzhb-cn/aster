package dev.agentmvp.background;

import dev.agentmvp.background.model.BackgroundTask;
import dev.agentmvp.background.model.TaskRun;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 后台任务持久化接口。
 *
 * <p>任务定义和任务运行记录分开保存。定义可以被更新或取消，
 * 运行记录只追加，用来留痕和后续让 Agent 查询。</p>
 */
public interface BackgroundTaskStore {
    /**
     * 保存一份任务定义。
     */
    void saveTask(BackgroundTask task) throws IOException;

    /**
     * 读取所有任务定义，返回每个 taskId 的最新版本。
     */
    List<BackgroundTask> listTasks() throws IOException;

    /**
     * 根据 id 查找任务定义。
     */
    Optional<BackgroundTask> findTask(String taskId) throws IOException;

    /**
     * 追加一条任务运行记录。
     */
    void appendRun(TaskRun run) throws IOException;

    /**
     * 读取任务运行记录。
     */
    List<TaskRun> listRuns() throws IOException;
}
