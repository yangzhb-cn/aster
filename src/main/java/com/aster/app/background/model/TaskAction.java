package com.aster.app.background.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 后台任务动作。
 *
 * <p>trigger 决定“什么时候执行”，action 决定“执行什么”。
 * 现在已经有 reminder 和 memory_extract。后续 notify、tool_call 等动作
 * 也会继续通过 action.type 分发到不同 handler。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TaskAction(
        String type,
        Map<String, Object> params
) {
}
