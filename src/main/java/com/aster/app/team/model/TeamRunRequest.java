package com.aster.app.team.model;

/**
 * Agent Team 单次运行请求。
 *
 * <p>Team 是一次性探索任务，因此模型选择会在启动时固定下来，
 * 避免运行过程中用户切换模型导致同一批成员使用不同模型。</p>
 */
public record TeamRunRequest(String task, String model) {
    /**
     * 解析 /team 后面的参数。
     *
     * <p>支持 {@code --model deepseek-v4-pro 任务} 和
     * {@code --model=deepseek-v4-pro 任务}；不传模型时 model 为空字符串。</p>
     */
    public static TeamRunRequest parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("--model=")) {
            String rest = value.substring("--model=".length()).trim();
            String[] parts = rest.split("\\s+", 2);
            return new TeamRunRequest(parts.length > 1 ? parts[1].trim() : "", parts[0].trim());
        }
        if (value.startsWith("--model ")) {
            String rest = value.substring("--model ".length()).trim();
            String[] parts = rest.split("\\s+", 2);
            return new TeamRunRequest(parts.length > 1 ? parts[1].trim() : "", parts[0].trim());
        }
        return new TeamRunRequest(value, "");
    }
}
