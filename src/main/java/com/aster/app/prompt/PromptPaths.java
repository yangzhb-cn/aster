package com.aster.app.prompt;

/**
 * 内置 prompt 资源路径。
 *
 * <p>这些路径是 classpath 路径，不是源码目录路径。
 * 也就是说运行 jar 时读取的是 jar 里的 prompts 分类目录。</p>
 */
public final class PromptPaths {
    public static final String SYSTEM = "/prompts/agent/system.md";
    public static final String CONTEXT_SUMMARY = "/prompts/context/summary.md";
    public static final String LONG_TERM_MEMORY_SYSTEM = "/prompts/memory/injection.md";
    public static final String MEMORY_EXTRACTION = "/prompts/memory/extraction.md";
    public static final String TEAM_PLANNER_SYSTEM = "/prompts/team/planner-system.md";
    public static final String TEAM_CODE_RESEARCHER_SYSTEM = "/prompts/team/code-researcher-system.md";
    public static final String TEAM_RISK_REVIEWER_SYSTEM = "/prompts/team/risk-reviewer-system.md";
    public static final String TEAM_FINAL_SUMMARY_USER = "/prompts/team/final-summary-user.md";
    public static final String PLAN_PLANNER_SYSTEM = "/prompts/plan/planner-system.md";
    public static final String PLAN_TASK_EXECUTOR_SYSTEM = "/prompts/plan/task-executor-system.md";
    public static final String PLAN_FINAL_SUMMARY_USER = "/prompts/plan/final-summary-user.md";

    private PromptPaths() {
    }
}
