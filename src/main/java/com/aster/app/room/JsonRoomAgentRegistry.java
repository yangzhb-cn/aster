package com.aster.app.room;

import com.aster.app.room.model.RoomAgentIndexData;
import com.aster.app.room.model.RoomAgentInput;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.llm.text.deepseek.DeepSeekModels;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * JSON 房间 Agent 注册表。
 *
 * <p>agents.json 保存可见元信息；system prompt 由 RoomAgentPromptStore 单独保存。
 * 删除 Agent 只归档，不删除 prompt 和历史 session。</p>
 */
public class JsonRoomAgentRegistry implements RoomAgentRegistry {
    private static final List<String> DEFAULT_TOOLS = List.of("read", "ls", "glob", "grep", "web_fetch", "web_search");
    private static final Set<String> FORBIDDEN_TOOLS = Set.of(
            "write",
            "edit",
            "bash",
            "background_task",
            "schedule",
            "todo",
            "todo_read",
            "todo_write",
            "subagent"
    );

    private final ObjectMapper objectMapper;
    private final Path file;
    private final RoomAgentPromptStore promptStore;

    public JsonRoomAgentRegistry(ObjectMapper objectMapper, Path file, RoomAgentPromptStore promptStore) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.file = Objects.requireNonNull(file);
        this.promptStore = Objects.requireNonNull(promptStore);
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            save(new RoomAgentIndexData(List.of()));
        }
    }

    /**
     * 列出未归档 Agent，按更新时间倒序。
     */
    @Override
    public synchronized List<RoomAgentProfile> listActive() throws IOException {
        return listAll().stream()
                .filter(agent -> !agent.archived())
                .toList();
    }

    /**
     * 列出已归档 Agent，按更新时间倒序。
     */
    @Override
    public synchronized List<RoomAgentProfile> listArchived() throws IOException {
        return listAll().stream()
                .filter(RoomAgentProfile::archived)
                .toList();
    }

    /**
     * 列出全部 Agent，包含已归档记录。
     */
    @Override
    public synchronized List<RoomAgentProfile> listAll() throws IOException {
        return load().agents().stream()
                .sorted(Comparator.comparing(RoomAgentProfile::updatedAt).reversed())
                .toList();
    }

    /**
     * 列出启用且未归档 Agent，按更新时间倒序。
     */
    @Override
    public synchronized List<RoomAgentProfile> listEnabled() throws IOException {
        return listActive().stream()
                .filter(RoomAgentProfile::enabled)
                .toList();
    }

    /**
     * 读取 Agent 配置。
     */
    @Override
    public synchronized Optional<RoomAgentProfile> get(String agentId) throws IOException {
        RoomIdValidator.requireSafeId(agentId, "agentId");
        return load().agents().stream()
                .filter(agent -> agent.agentId().equals(agentId))
                .findFirst();
    }

    /**
     * 创建 Agent，并写入外部 system prompt。
     */
    @Override
    public synchronized RoomAgentProfile create(RoomAgentInput input) throws IOException {
        Objects.requireNonNull(input);
        RoomAgentProfile draft = RoomAgentProfile.create(
                input.name(),
                input.role(),
                input.description(),
                "",
                normalizeAliases(input.mentionAliases(), input.name()),
                normalizeTools(input.toolAllowlist()),
                normalizeModel(input.model()),
                input.enabled() == null || input.enabled()
        );
        String promptPath = promptStore.write(draft.agentId(), input.systemPrompt());
        RoomAgentProfile profile = draft.updated(
                draft.name(),
                draft.role(),
                draft.description(),
                promptPath,
                draft.mentionAliases(),
                draft.toolAllowlist(),
                draft.model(),
                draft.enabled()
        );

        RoomAgentIndexData data = load();
        List<RoomAgentProfile> agents = new ArrayList<>(data.agents());
        agents.add(profile);
        validateMentionUniqueness(agents);
        save(new RoomAgentIndexData(agents));
        return profile;
    }

    /**
     * 更新 Agent 配置和 system prompt。
     */
    @Override
    public synchronized RoomAgentProfile update(RoomAgentInput input) throws IOException {
        Objects.requireNonNull(input);
        String agentId = requireAgentId(input.agentId());
        RoomAgentIndexData data = load();
        List<RoomAgentProfile> agents = new ArrayList<>();
        RoomAgentProfile updated = null;
        for (RoomAgentProfile agent : data.agents()) {
            if (agent.agentId().equals(agentId)) {
                String promptPath = promptStore.write(agentId, input.systemPrompt());
                updated = agent.updated(
                        input.name(),
                        input.role(),
                        input.description(),
                        promptPath,
                        normalizeAliases(input.mentionAliases(), input.name()),
                        normalizeTools(input.toolAllowlist()),
                        normalizeModel(input.model()),
                        input.enabled() == null ? agent.enabled() : input.enabled()
                );
                agents.add(updated);
            } else {
                agents.add(agent);
            }
        }
        if (updated == null) {
            throw new IOException("room agent not found: " + agentId);
        }
        validateMentionUniqueness(agents);
        save(new RoomAgentIndexData(agents));
        return updated;
    }

    /**
     * 归档 Agent。
     */
    @Override
    public synchronized RoomAgentProfile archive(String agentId) throws IOException {
        RoomIdValidator.requireSafeId(agentId, "agentId");
        RoomAgentIndexData data = load();
        List<RoomAgentProfile> agents = new ArrayList<>();
        RoomAgentProfile archived = null;
        for (RoomAgentProfile agent : data.agents()) {
            if (agent.agentId().equals(agentId)) {
                archived = agent.markArchived();
                agents.add(archived);
            } else {
                agents.add(agent);
            }
        }
        if (archived == null) {
            throw new IOException("room agent not found: " + agentId);
        }
        save(new RoomAgentIndexData(agents));
        return archived;
    }

    /**
     * 从归档恢复 Agent。
     */
    @Override
    public synchronized RoomAgentProfile restore(String agentId) throws IOException {
        RoomIdValidator.requireSafeId(agentId, "agentId");
        RoomAgentIndexData data = load();
        List<RoomAgentProfile> agents = new ArrayList<>();
        RoomAgentProfile restored = null;
        for (RoomAgentProfile agent : data.agents()) {
            if (agent.agentId().equals(agentId)) {
                restored = agent.restored();
                agents.add(restored);
            } else {
                agents.add(agent);
            }
        }
        if (restored == null) {
            throw new IOException("room agent not found: " + agentId);
        }
        validateMentionUniqueness(agents);
        save(new RoomAgentIndexData(agents));
        return restored;
    }

    /**
     * 从 agents.json 中物理删除 Agent 配置。
     */
    @Override
    public synchronized RoomAgentProfile deletePermanently(String agentId) throws IOException {
        RoomIdValidator.requireSafeId(agentId, "agentId");
        RoomAgentIndexData data = load();
        List<RoomAgentProfile> agents = new ArrayList<>();
        RoomAgentProfile deleted = null;
        for (RoomAgentProfile agent : data.agents()) {
            if (agent.agentId().equals(agentId)) {
                deleted = agent;
            } else {
                agents.add(agent);
            }
        }
        if (deleted == null) {
            throw new IOException("room agent not found: " + agentId);
        }
        if (!deleted.archived()) {
            throw new IOException("room agent must be archived before physical delete: " + agentId);
        }
        save(new RoomAgentIndexData(agents));
        return deleted;
    }

    private RoomAgentIndexData load() throws IOException {
        if (!Files.exists(file)) {
            return new RoomAgentIndexData(List.of());
        }
        return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), RoomAgentIndexData.class);
    }

    private void save(RoomAgentIndexData data) throws IOException {
        Files.writeString(
                file,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                StandardCharsets.UTF_8
        );
    }

    private String requireAgentId(String agentId) throws IOException {
        try {
            RoomIdValidator.requireSafeId(agentId, "agentId");
            return agentId;
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private List<String> normalizeAliases(List<String> aliases, String name) {
        List<String> values = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            values.add(name.trim());
        }
        if (aliases != null) {
            values.addAll(aliases);
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> normalizeTools(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return DEFAULT_TOOLS;
        }
        return tools.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .filter(value -> !FORBIDDEN_TOOLS.contains(value))
                .distinct()
                .toList();
    }

    private String normalizeModel(String model) throws IOException {
        String selected = model == null || model.isBlank() ? DeepSeekModels.V4_FLASH : model.trim();
        if (!DeepSeekModels.switchableChatModels().contains(selected)) {
            throw new IOException("unsupported room agent model: " + selected);
        }
        return selected;
    }

    private void validateMentionUniqueness(List<RoomAgentProfile> agents) throws IOException {
        Set<String> seen = new HashSet<>();
        for (RoomAgentProfile agent : agents) {
            if (agent.archived()) {
                continue;
            }
            for (String alias : agent.mentionAliases()) {
                String key = alias.toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    throw new IOException("duplicate room agent mention alias: @" + alias);
                }
            }
        }
    }
}
