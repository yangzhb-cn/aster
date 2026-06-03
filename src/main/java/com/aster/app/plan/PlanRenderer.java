package com.aster.app.plan;

import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanRunResult;
import com.aster.app.plan.model.PlanTask;

/**
 * Plan DAG 的 Markdown 渲染器。
 *
 * <p>UI 展示和最终交给主 Agent 整理的材料都从这里生成，避免各入口重复拼字符串。</p>
 */
public final class PlanRenderer {
    private PlanRenderer() {
    }

    /**
     * 渲染待执行 DAG，并提示用户下一步命令。
     */
    public static String renderPlan(ExecutionPlan plan) {
        StringBuilder output = new StringBuilder();
        output.append("# Plan DAG\n\n");
        output.append("目标：").append(plan.task()).append("\n\n");
        for (PlanTask task : plan.tasks()) {
            output.append("- ").append(task.id())
                    .append(" [").append(task.type()).append("]")
                    .append(" deps: ").append(task.dependencies().isEmpty() ? "-" : String.join(", ", task.dependencies()))
                    .append('\n')
                    .append("  ").append(task.description()).append('\n');
        }
        output.append("\n输入 /start 执行；输入 /plan <新目标> 重新计划；输入 /plan cancel 取消。");
        return output.toString();
    }

    /**
     * 渲染执行后的完整材料，供主 Agent 最终整理。
     */
    public static String renderExecutionMaterial(ExecutionPlan plan, PlanRunResult result) {
        StringBuilder output = new StringBuilder();
        output.append("# Plan 执行材料\n\n");
        output.append("原始目标：").append(plan.task()).append("\n\n");
        output.append("执行结果：").append(result.success() ? "SUCCESS" : "FAILED").append("\n");
        if (!result.success()) {
            output.append("失败原因：").append(result.failure()).append("\n");
        }
        output.append('\n');
        for (PlanTask task : plan.tasks()) {
            output.append("## ").append(task.id()).append(" ").append(task.type()).append("\n");
            output.append("- 描述：").append(task.description()).append('\n');
            output.append("- 依赖：").append(task.dependencies().isEmpty() ? "无" : String.join(", ", task.dependencies())).append('\n');
            output.append("- 状态：").append(task.status()).append('\n');
            output.append("- 尝试次数：").append(task.attempts()).append("\n\n");
            output.append(task.result()).append("\n\n");
        }
        return output.toString().stripTrailing();
    }
}
