package com.aster.core.session.model;

/**
 * Session 索引记录。
 *
 * <p>id 是稳定机器标识，也是 JSONL 文件名；displayName 只用于 UI 展示，
 * 可以重命名，不影响审计文件。</p>
 */
public record SessionRecord(
        String id,
        String displayName,
        String createdAt,
        String updatedAt,
        boolean archived
) {
    /**
     * 返回更新展示名后的记录。
     */
    public SessionRecord withDisplayName(String nextDisplayName, String updatedAt) {
        return new SessionRecord(id, nextDisplayName, createdAt, updatedAt, archived);
    }

    /**
     * 返回归档后的记录。
     */
    public SessionRecord archived(String updatedAt) {
        return new SessionRecord(id, displayName, createdAt, updatedAt, true);
    }

    /**
     * 返回恢复为未归档后的记录。
     */
    public SessionRecord restored(String updatedAt) {
        return new SessionRecord(id, displayName, createdAt, updatedAt, false);
    }

    /**
     * 返回刷新更新时间后的记录。
     */
    public SessionRecord touched(String updatedAt) {
        return new SessionRecord(id, displayName, createdAt, updatedAt, archived);
    }
}
