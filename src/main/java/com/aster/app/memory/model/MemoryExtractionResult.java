package com.aster.app.memory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Memory Agent 的结构化输出。
 *
 * <p>提示词要求模型只输出 JSON 对象：{@code {"memories":[...]}}。
 * 这里用 record 接住结果，后续由 MarkdownMemoryStore 做最终写入校验。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemoryExtractionResult(
        List<MemoryCandidate> memories
) {
    public MemoryExtractionResult {
        if (memories == null) {
            memories = List.of();
        }
    }
}
