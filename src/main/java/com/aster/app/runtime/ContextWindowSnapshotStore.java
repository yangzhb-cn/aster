package com.aster.app.runtime;

import com.aster.core.context.model.ContextWindowSnapshot;

import java.io.IOException;
import java.util.Optional;

/**
 * 上下文窗口快照存储。
 *
 * <p>快照是可覆盖缓存，和追加式 session JSONL 分开保存。
 * 读取失败或校验失败时，上层应回退到从 JSONL 全量重建。</p>
 */
public interface ContextWindowSnapshotStore {
    /**
     * 读取指定 session/branch 的最新快照。
     */
    Optional<ContextWindowSnapshot> load(String sessionId, String branchId) throws IOException;

    /**
     * 覆盖保存最新快照。
     */
    void save(ContextWindowSnapshot snapshot) throws IOException;
}
