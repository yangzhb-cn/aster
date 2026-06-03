package dev.agentmvp.app.tool.builtin.model;

import java.util.Map;

/**
 * 内置工具参数读取工具类。
 *
 * <p>LocalToolExecutor 已经把模型传来的 JSON 字符串解析成 Map。
 * 这里再把 Map 里的字段转换成具体参数类型，让每个工具的参数模型更清楚。</p>
 */
final class ToolParamReader {
    private ToolParamReader() {
    }

    /**
     * 读取必填字符串参数。
     */
    static String requiredString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("缺少必填字符串参数: " + name);
        }
        return text;
    }

    /**
     * 读取必填字符串参数，但允许空字符串。
     */
    static String requiredStringAllowEmpty(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("缺少必填字符串参数: " + name);
        }
        return text;
    }

    /**
     * 读取可选整数参数，并限制最大值。
     */
    static int optionalInt(Map<String, Object> arguments, String name, int defaultValue, int maxValue) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            int intValue = number.intValue();
            if (intValue < 0) {
                throw new IllegalArgumentException("参数不能是负数: " + name);
            }
            return Math.min(intValue, maxValue);
        }
        throw new IllegalArgumentException("参数必须是数字: " + name);
    }

}
