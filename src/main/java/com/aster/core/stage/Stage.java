package com.aster.core.stage;

/**
 * Agent 主流程里的必经阶段。
 *
 * <p>Stage 和 Hook 的区别是：Stage 是系统自己定义的固定流程，
 * 例如读取会话历史、构造上下文、协议校验。没有注册任何 Hook 时，
 * Stage 仍然必须执行，否则 Agent 主流程就不完整。</p>
 */
public interface Stage {
    /**
     * 稳定名称，方便日志、教程或后续事件展示引用。
     */
    String name();
}
