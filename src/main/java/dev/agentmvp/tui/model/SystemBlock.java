package dev.agentmvp.tui.model;

/**
 * 系统提示展示块。
 *
 * <p>例如启动失败、API Key 缺失这类信息，不属于 assistant 正文。</p>
 */
public record SystemBlock(String text) implements UiBlock {
}
