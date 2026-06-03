package dev.agentmvp.ui.tui.model;

/**
 * assistant 正文展示块。
 *
 * <p>正文是流式到达的，所以内部用 StringBuilder 逐步追加。</p>
 */
public final class AssistantBlock implements UiBlock {
    private final StringBuilder text = new StringBuilder();

    public String text() {
        return text.toString();
    }

    /**
     * 追加一段流式正文。
     */
    public void append(String value) {
        text.append(value);
    }
}
