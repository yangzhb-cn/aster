package com.aster.app.team.model;

/**
 * 一次 Agent Team 探索的完整结果。
 *
 * <p>TeamRunFinished 事件只用于 UI 展示状态；完整材料保存在这里，
 * 由运行时交给主 Agent 整理最终回答。</p>
 */
public record TeamRunOutput(
        String task,
        boolean success,
        String material,
        String failure,
        long elapsedMillis
) {
}
