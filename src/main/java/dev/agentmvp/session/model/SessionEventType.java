package dev.agentmvp.session.model;

/**
 * JSONL session 事件类型。
 *
 * <p>事件日志是 append-only 的，消息、分支、运行状态都通过事件表达。
 * 这样历史可以回放、分支可以从某个 seq 派生，审计时也能看到每一步发生了什么。</p>
 */
public enum SessionEventType {
    SESSION_CREATED("session_created"),
    BRANCH_CREATED("branch_created"),
    RUN_STARTED("run_started"),
    RUN_FINISHED("run_finished"),
    RUN_INTERRUPTED("run_interrupted"),
    MESSAGE_APPENDED("message_appended");

    private final String value;

    SessionEventType(String value) {
        this.value = value;
    }

    /**
     * 写入 JSONL 的稳定字符串。
     */
    public String value() {
        return value;
    }
}
