package com.aster;

import com.aster.app.prompt.PromptLoader;
import com.aster.app.prompt.PromptPaths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置 prompt 资源读取测试。
 */
class PromptLoaderTest {
    /**
     * 验证 jar classpath 里的 Markdown prompt 都能读取。
     */
    @Test
    void loadsBuiltinMarkdownPromptsFromClasspath() throws Exception {
        PromptLoader loader = new PromptLoader();

        String systemPrompt = loader.load(PromptPaths.SYSTEM);
        String summaryPrompt = loader.load(PromptPaths.CONTEXT_SUMMARY);
        String memorySystemPrompt = loader.load(PromptPaths.LONG_TERM_MEMORY_SYSTEM);
        String memoryExtractionPrompt = loader.load(PromptPaths.MEMORY_EXTRACTION);
        String teamPlannerPrompt = loader.load(PromptPaths.TEAM_PLANNER_SYSTEM);
        String teamCodeResearcherPrompt = loader.load(PromptPaths.TEAM_CODE_RESEARCHER_SYSTEM);
        String teamRiskReviewerPrompt = loader.load(PromptPaths.TEAM_RISK_REVIEWER_SYSTEM);
        String teamFinalSummaryPrompt = loader.load(PromptPaths.TEAM_FINAL_SUMMARY_USER);

        assertTrue(systemPrompt.contains("Aster System Prompt"));
        assertTrue(systemPrompt.contains("Skill 使用"));
        assertTrue(summaryPrompt.contains("Context Summary Prompt"));
        assertTrue(summaryPrompt.contains("工具协议安全"));
        assertTrue(memorySystemPrompt.contains("{{memory}}"));
        assertTrue(memoryExtractionPrompt.contains("USER_PROFILE"));
        assertTrue(memoryExtractionPrompt.contains("BEHAVIOR_PREFERENCE"));
        assertTrue(teamPlannerPrompt.contains("planner"));
        assertTrue(teamCodeResearcherPrompt.contains("code_researcher"));
        assertTrue(teamRiskReviewerPrompt.contains("risk_reviewer"));
        assertTrue(teamFinalSummaryPrompt.contains("{{team_material}}"));
    }

    /**
     * 验证缺失资源会直接报错，避免静默使用空 prompt。
     */
    @Test
    void failsWhenPromptResourceDoesNotExist() {
        PromptLoader loader = new PromptLoader();

        assertThrows(IllegalArgumentException.class, () -> loader.load("/prompts/missing.md"));
    }
}
