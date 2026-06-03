package dev.agentmvp.app.memory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Memory Agent 抽取出的一条候选长期记忆。
 *
 * <p>它还不是最终写入结果。写入前会再次校验类型、内容和证据，
 * 只有符合四类约束的候选才会进入 Markdown 长期记忆文件。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemoryCandidate(
        MemoryType type,
        String content,
        String evidence
) {
}
