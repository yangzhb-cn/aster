package dev.agentmvp.ui.tui.model;

/**
 * 工具调用展示块。
 *
 * <p>工具块需要在开始时创建，在结束时补充输出、状态和耗时。
 * 因此它是一个可变对象，而不是普通不可变 record。</p>
 */
public final class ToolBlock implements UiBlock {
    private static final int COLLAPSE_LINE_THRESHOLD = 12;
    private static final int COLLAPSE_CHAR_THRESHOLD = 1_200;

    private final String toolCallId;
    private final String toolName;
    private final String argumentsJson;
    private String outputText = "";
    private ToolStatus status = ToolStatus.RUNNING;
    private long elapsedMillis;
    private boolean collapsed;

    public ToolBlock(String toolCallId, String toolName, String argumentsJson) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
    }

    public String toolCallId() {
        return toolCallId;
    }

    public String toolName() {
        return toolName;
    }

    public String argumentsJson() {
        return argumentsJson;
    }

    public String outputText() {
        return outputText;
    }

    public ToolStatus status() {
        return status;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public boolean collapsed() {
        return collapsed;
    }

    /**
     * 判断这个工具块是否有可折叠输出。
     */
    public boolean collapsible() {
        return outputText != null && !outputText.isBlank();
    }

    /**
     * 设置折叠状态。
     */
    public void setCollapsed(boolean collapsed) {
        if (collapsible()) {
            this.collapsed = collapsed;
        }
    }

    /**
     * 标记工具执行完成。
     */
    public void finish(String outputText, boolean success, long elapsedMillis) {
        this.outputText = outputText == null ? "" : outputText;
        this.status = success ? ToolStatus.DONE : ToolStatus.FAILED;
        this.elapsedMillis = elapsedMillis;
        this.collapsed = shouldCollapse(this.outputText);
    }

    private boolean shouldCollapse(String value) {
        return value.length() > COLLAPSE_CHAR_THRESHOLD || value.lines().count() > COLLAPSE_LINE_THRESHOLD;
    }
}
