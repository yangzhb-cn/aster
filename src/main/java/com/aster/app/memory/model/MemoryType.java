package com.aster.app.memory.model;

/**
 * 长期记忆类型。
 *
 * <p>教学版只允许这四种类型。后台 Memory Agent 即使输出了其它分类，
 * Java 反序列化或写入校验也不会接受，避免长期记忆变成随意笔记。</p>
 */
public enum MemoryType {
    USER_PROFILE("用户画像"),
    BEHAVIOR_PREFERENCE("行为偏好"),
    PROJECT_DYNAMIC("项目动态"),
    EXTERNAL_POINTER("外部指针");

    private final String heading;

    MemoryType(String heading) {
        this.heading = heading;
    }

    /**
     * 对应 Markdown 文件里的二级标题。
     */
    public String heading() {
        return heading;
    }
}
