package com.aster.app.team.model;

/**
 * 固定探索团队的成员角色。
 */
public enum TeamRole {
    PLANNER("planner", "拆解探索路径"),
    CODE_RESEARCHER("code_researcher", "阅读代码并整理证据"),
    RISK_REVIEWER("risk_reviewer", "审查风险和遗漏");

    private final String id;
    private final String title;

    TeamRole(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }
}
