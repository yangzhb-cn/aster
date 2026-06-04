package com.aster.app.todo;

import com.aster.app.todo.model.TodoItem;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 便签待办存储接口。
 *
 * <p>Web API、Agent todo 工具和后台扫描器都通过它读写同一份清单。</p>
 */
public interface TodoStore {
    /**
     * 列出全部非归档待办。
     */
    List<TodoItem> listActive() throws IOException;

    /**
     * 列出全部已归档待办。
     */
    List<TodoItem> listArchived() throws IOException;

    /**
     * 新增待办。
     */
    TodoItem add(String content, String priority, String dueAt) throws IOException;

    /**
     * 更新待办内容。
     */
    TodoItem update(String id, String content, String priority, String dueAt) throws IOException;

    /**
     * 标记完成。
     */
    TodoItem complete(String id, String result) throws IOException;

    /**
     * 归档待办。
     */
    TodoItem archive(String id) throws IOException;

    /**
     * 从归档恢复待办。
     */
    TodoItem restore(String id) throws IOException;

    /**
     * 物理删除待办。
     */
    TodoItem deletePermanently(String id) throws IOException;

    /**
     * 找出已到期的待办。
     */
    List<TodoItem> dueItems(Instant now) throws IOException;

    /**
     * 查找待办。
     */
    Optional<TodoItem> find(String id) throws IOException;
}
