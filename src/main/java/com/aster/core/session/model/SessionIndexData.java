package com.aster.core.session.model;

import java.util.List;

/**
 * workspace/sessions/index.json 的根对象。
 */
public record SessionIndexData(
        List<SessionRecord> sessions
) {
    /**
     * 创建空索引。
     */
    public static SessionIndexData empty() {
        return new SessionIndexData(List.of());
    }

    public SessionIndexData {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }
}
