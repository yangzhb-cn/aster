package dev.agentmvp.app.tool.builtin.model;

import java.util.Map;

/**
 * read 工具的入参。
 *
 * <p>path 是要读取的文件路径。offsetBytes 支持按字节偏移读取；
 * page/pageSizeBytes 提供更像“分页”的读取方式。</p>
 */
public record ReadToolParams(String path, int offsetBytes, int maxBytes) {
    private static final int DEFAULT_MAX_BYTES = 200_000;
    private static final int HARD_MAX_BYTES = 1_000_000;

    /**
     * 从工具参数 Map 构造 read 入参。
     */
    public static ReadToolParams from(Map<String, Object> arguments) {
        int maxBytes = ToolParamReader.optionalInt(arguments, "maxBytes", DEFAULT_MAX_BYTES, HARD_MAX_BYTES);
        int offsetBytes = ToolParamReader.optionalInt(arguments, "offsetBytes", 0, Integer.MAX_VALUE);

        int page = ToolParamReader.optionalInt(arguments, "page", 0, Integer.MAX_VALUE);
        if (page > 0) {
            int pageSizeBytes = ToolParamReader.optionalInt(arguments, "pageSizeBytes", DEFAULT_MAX_BYTES, HARD_MAX_BYTES);
            offsetBytes = Math.multiplyExact(page - 1, pageSizeBytes);
            maxBytes = pageSizeBytes;
        }

        return new ReadToolParams(
                ToolParamReader.requiredString(arguments, "path"),
                offsetBytes,
                maxBytes
        );
    }
}
