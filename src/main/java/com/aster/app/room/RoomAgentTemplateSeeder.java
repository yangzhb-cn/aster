package com.aster.app.room;

import com.aster.app.room.model.RoomAgentInput;
import com.aster.app.room.model.RoomAgentTemplate;
import com.aster.app.room.model.RoomAgentTemplateData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 房间 Agent 示例模板导入器。
 *
 * <p>它只在当前没有任何 Room Agent 记录时导入默认模板。
 * 模板内容放在 resources 里，导入后会落到 workspace，用户可以在 Web 中继续修改。</p>
 */
public class RoomAgentTemplateSeeder {
    private static final String TEMPLATE_INDEX = "/prompts/room/default-agents.json";

    private final ObjectMapper objectMapper;
    private final RoomAgentRegistry registry;

    public RoomAgentTemplateSeeder(ObjectMapper objectMapper, RoomAgentRegistry registry) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.registry = Objects.requireNonNull(registry);
    }

    /**
     * 当前没有任何 Agent 记录时，导入默认六角色模板。
     */
    public void seedIfEmpty() throws IOException {
        if (!registry.listAll().isEmpty()) {
            return;
        }
        for (RoomAgentTemplate template : loadTemplateData().agents()) {
            registry.create(new RoomAgentInput(
                    null,
                    template.name(),
                    template.role(),
                    template.description(),
                    loadTextResource(template.promptPath()),
                    template.mentionAliases(),
                    template.toolAllowlist(),
                    template.model(),
                    template.enabled() == null || template.enabled()
            ));
        }
    }

    private RoomAgentTemplateData loadTemplateData() throws IOException {
        try (InputStream input = resource(TEMPLATE_INDEX)) {
            return objectMapper.readValue(input, RoomAgentTemplateData.class);
        }
    }

    private String loadTextResource(String path) throws IOException {
        try (InputStream input = resource(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream resource(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("room agent template resource path is blank");
        }
        InputStream input = RoomAgentTemplateSeeder.class.getResourceAsStream(path);
        if (input == null) {
            throw new IOException("room agent template resource not found: " + path);
        }
        return input;
    }
}
