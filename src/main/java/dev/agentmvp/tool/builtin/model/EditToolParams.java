package dev.agentmvp.tool.builtin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * edit 工具的入参。
 *
 * <p>replacements 支持一次提交多个精确替换。每个 oldText 都必须在原文件中
 * 只出现一次，并且多个替换区域不能互相重叠。</p>
 */
public record EditToolParams(String path, List<Replacement> replacements) {
    /**
     * 从工具参数 Map 构造 edit 入参。
     */
    public static EditToolParams from(Map<String, Object> arguments) {
        String path = ToolParamReader.requiredString(arguments, "path");
        Object rawReplacements = arguments.get("replacements");
        if (rawReplacements instanceof List<?> list && !list.isEmpty()) {
            List<Replacement> replacements = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("replacements 里的每一项都必须是对象");
                }
                replacements.add(new Replacement(
                        ToolParamReader.requiredString(castMap(map), "oldText"),
                        ToolParamReader.requiredStringAllowEmpty(castMap(map), "newText")
                ));
            }
            return new EditToolParams(path, List.copyOf(replacements));
        }

        return new EditToolParams(
                path,
                List.of(new Replacement(
                        ToolParamReader.requiredString(arguments, "oldText"),
                        ToolParamReader.requiredStringAllowEmpty(arguments, "newText")
                ))
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /**
     * 一个精确替换区域。
     */
    public record Replacement(String oldText, String newText) {
    }
}
