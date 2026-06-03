package com.aster.app.team.model;

import java.util.Objects;

/**
 * Agent Team 各角色的 system prompt 集合。
 */
public record TeamPromptSet(
        String plannerSystem,
        String codeResearcherSystem,
        String riskReviewerSystem
) {
    public TeamPromptSet {
        Objects.requireNonNull(plannerSystem);
        Objects.requireNonNull(codeResearcherSystem);
        Objects.requireNonNull(riskReviewerSystem);
    }

    /**
     * 按 Team 角色返回对应 system prompt。
     */
    public String systemPrompt(TeamRole role) {
        return switch (role) {
            case PLANNER -> plannerSystem;
            case CODE_RESEARCHER -> codeResearcherSystem;
            case RISK_REVIEWER -> riskReviewerSystem;
        };
    }
}
