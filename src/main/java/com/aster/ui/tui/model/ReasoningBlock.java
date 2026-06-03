package com.aster.ui.tui.model;

/**
 * DeepSeek reasoning_content 展示块。
 *
 * <p>这里显示的是模型 API 明确返回的 {@code reasoning_content}。
 * 它不是宿主程序自己推断出来的隐藏思维链。</p>
 */
public final class ReasoningBlock implements UiBlock {
    private final StringBuilder text = new StringBuilder();

    public String text() {
        return text.toString();
    }

    /**
     * 追加一段流式 thinking 文本。
     */
    public void append(String value) {
        text.append(value);
    }
}
