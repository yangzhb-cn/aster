package dev.agentmvp.tui.model;

/**
 * TUI 消息区的一块可渲染内容。
 *
 * <p>旧逻辑把所有内容都塞进字符串列表里，再靠前缀判断颜色。
 * 这样很难展示“思考、工具、正文”这些不同语义。
 * 现在每一种展示内容都有自己的 block 类型，TUI 只负责按类型绘制。</p>
 */
public sealed interface UiBlock permits UserBlock, AssistantBlock, ReasoningBlock, ToolBlock, SystemBlock, ErrorBlock {
}
