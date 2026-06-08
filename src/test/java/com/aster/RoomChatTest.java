package com.aster;

import com.aster.app.room.JsonRoomAgentRegistry;
import com.aster.app.room.JsonRoomMembershipStore;
import com.aster.app.room.JsonRoomStore;
import com.aster.app.room.RoomAgentPromptStore;
import com.aster.app.room.RoomAgentTemplateSeeder;
import com.aster.app.room.RoomContextInjectHook;
import com.aster.app.room.RoomMentionParser;
import com.aster.app.room.RoomPromptBuilder;
import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentInput;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomMembership;
import com.aster.app.room.RoomAgentRunContext;
import com.aster.core.agent.control.AgentRunControl;
import com.aster.core.hook.BeforeLlmRequestContext;
import com.aster.llm.text.deepseek.DeepSeekModels;
import com.aster.llm.text.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多 Agent 聊天室 MVP 测试。
 */
class RoomChatTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * 验证 @Agent 和 @all 能按注册表顺序解析。
     */
    @Test
    void parsesMentionedRoomAgents() {
        RoomMentionParser parser = new RoomMentionParser();
        RoomAgentProfile pm = agent("a1", "产品", List.of("产品", "pm"));
        RoomAgentProfile fe = agent("a2", "前端", List.of("前端", "fe"));

        List<RoomAgentProfile> one = parser.parse("@pm 看一下需求", List.of(pm, fe));
        List<RoomAgentProfile> all = parser.parse("@all 都说说", List.of(pm, fe));

        assertEquals(List.of(pm), one);
        assertEquals(List.of(pm, fe), all);
    }

    /**
     * 验证 Agent 注册表写入外部 prompt，归档不会删除历史配置文件。
     */
    @Test
    void storesRoomAgentProfileAndPrompt() throws Exception {
        RoomAgentPromptStore promptStore = new RoomAgentPromptStore(tempDir.resolve("prompts"));
        JsonRoomAgentRegistry registry = new JsonRoomAgentRegistry(
                objectMapper,
                tempDir.resolve("agents.json"),
                promptStore
        );

        RoomAgentProfile created = registry.create(new RoomAgentInput(
                null,
                "架构师",
                "负责架构判断",
                "关注边界和依赖",
                "你是架构师。",
                List.of("arch"),
                List.of("read", "bash"),
                DeepSeekModels.V4_PRO,
                true
        ));

        assertEquals(1, registry.listActive().size());
        assertTrue(promptStore.read(created).contains("你是架构师"));
        assertEquals(List.of("read"), created.toolAllowlist());
        assertEquals(DeepSeekModels.V4_PRO, created.model());

        registry.archive(created.agentId());

        assertTrue(registry.listActive().isEmpty());
        assertTrue(promptStore.read(created).contains("你是架构师"));
    }

    /**
     * 验证房间上下文只临时注入 LLM 请求，不改原始 user 消息对象。
     */
    @Test
    void injectsRoomContextIntoLastUserMessage() throws Exception {
        ChatRoom room = ChatRoom.create("方案讨论");
        RoomAgentProfile agent = agent("agent_arch", "架构师", List.of("架构师"));
        HubMessage trigger = HubMessage.user(room.roomId(), "@架构师 看下方案", List.of(agent.agentId()));
        RoomContextInjectHook hook = new RoomContextInjectHook(
                new RoomAgentRunContext(room, agent, trigger, List.of(
                        HubMessage.user(room.roomId(), "我们要做聊天室", List.of()),
                        trigger
                )),
                new RoomPromptBuilder("{{agent_prompt}}")
        );

        BeforeLlmRequestContext before = new BeforeLlmRequestContext(
                "room-session",
                "run-1",
                1,
                "model",
                128_000,
                "",
                new AgentRunControl(),
                List.of(Message.system("system"), Message.user("@架构师 看下方案")),
                List.of()
        );

        BeforeLlmRequestContext after = hook.handle(before);

        assertEquals("@架构师 看下方案", before.messages().get(1).content());
        assertTrue(after.messages().get(1).content().startsWith("<system-reminder>"));
        assertTrue(after.messages().get(1).content().contains("最近共享消息"));
        assertTrue(after.messages().get(1).content().contains("@架构师 看下方案"));
        assertFalse(after.messages().get(0).content().contains("房间上下文"));
    }

    /**
     * 验证默认模板只在空注册表里导入一次。
     */
    @Test
    void seedsDefaultRoomAgentsOnlyOnce() throws Exception {
        RoomAgentPromptStore promptStore = new RoomAgentPromptStore(tempDir.resolve("prompts"));
        JsonRoomAgentRegistry registry = new JsonRoomAgentRegistry(
                objectMapper,
                tempDir.resolve("agents.json"),
                promptStore
        );
        RoomAgentTemplateSeeder seeder = new RoomAgentTemplateSeeder(objectMapper, registry);

        seeder.seedIfEmpty();
        seeder.seedIfEmpty();

        List<RoomAgentProfile> agents = registry.listActive();
        assertEquals(6, agents.size());
        assertTrue(agents.stream().anyMatch(agent -> agent.name().equals("产品经理")));
        assertTrue(agents.stream().anyMatch(agent -> agent.name().equals("前端")));
        assertTrue(agents.stream().anyMatch(agent -> agent.name().equals("后端")));
        assertTrue(agents.stream().anyMatch(agent -> agent.name().equals("测试")));
        assertTrue(agents.stream().anyMatch(agent -> agent.name().equals("评审")));
        assertTrue(agents.stream().anyMatch(agent -> agent.name().equals("架构师")));
        RoomAgentProfile architect = agents.stream()
                .filter(agent -> agent.name().equals("架构师"))
                .findFirst()
                .orElseThrow();
        assertTrue(promptStore.read(architect).contains("你是架构师 Agent"));
    }

    /**
     * 验证聊天室元信息支持归档、恢复和物理删除。
     */
    @Test
    void restoresAndPhysicallyDeletesArchivedRooms() throws Exception {
        JsonRoomStore store = new JsonRoomStore(objectMapper, tempDir.resolve("rooms.json"));
        ChatRoom room = store.create("归档房间");

        store.archive(room.roomId());
        assertEquals(1, store.listArchived().size());
        assertTrue(store.listActive().isEmpty());

        store.restore(room.roomId());
        assertEquals(room.roomId(), store.listActive().getFirst().roomId());

        store.archive(room.roomId());
        store.deletePermanently(room.roomId());

        assertTrue(store.listArchived().isEmpty());
        assertTrue(store.get(room.roomId()).isEmpty());
    }

    /**
     * 验证 Room Agent 支持归档、恢复和物理删除配置。
     */
    @Test
    void restoresAndPhysicallyDeletesArchivedRoomAgents() throws Exception {
        RoomAgentPromptStore promptStore = new RoomAgentPromptStore(tempDir.resolve("prompts"));
        JsonRoomAgentRegistry registry = new JsonRoomAgentRegistry(
                objectMapper,
                tempDir.resolve("agents.json"),
                promptStore
        );
        RoomAgentProfile created = registry.create(new RoomAgentInput(
                null,
                "评审",
                "代码审查",
                "",
                "你是评审。",
                List.of("review"),
                List.of("read"),
                DeepSeekModels.V4_FLASH,
                true
        ));

        registry.archive(created.agentId());
        assertEquals(1, registry.listArchived().size());

        registry.restore(created.agentId());
        assertEquals(created.agentId(), registry.listActive().getFirst().agentId());

        registry.archive(created.agentId());
        registry.deletePermanently(created.agentId());

        assertTrue(registry.listArchived().isEmpty());
        assertTrue(registry.get(created.agentId()).isEmpty());
    }

    /**
     * 验证聊天室成员关系按顺序初始化，移除后恢复会递增 generation。
     */
    @Test
    void archivesAndRestoresRoomMembershipWithNewGeneration() throws Exception {
        JsonRoomMembershipStore store = new JsonRoomMembershipStore(objectMapper, tempDir.resolve("members.json"));
        RoomAgentProfile pm = agent("agent_pm", "产品", List.of("pm"));
        RoomAgentProfile fe = agent("agent_fe", "前端", List.of("fe"));

        List<RoomMembership> initial = store.ensureRoomMembers("room_a", List.of(pm, fe));

        assertEquals(2, initial.size());
        assertEquals("agent_pm", initial.get(0).agentId());
        assertEquals(0, initial.get(0).orderIndex());
        assertEquals(1, initial.get(0).generation());

        RoomMembership archived = store.archive("room_a", "agent_pm");
        assertTrue(archived.archived());
        assertEquals(List.of("agent_fe"), store.listActive("room_a").stream().map(RoomMembership::agentId).toList());

        RoomMembership restored = store.restore("room_a", "agent_pm");
        assertFalse(restored.archived());
        assertEquals(2, restored.generation());
        assertEquals(List.of("agent_pm", "agent_fe"), store.listActive("room_a").stream().map(RoomMembership::agentId).toList());
    }

    private RoomAgentProfile agent(String id, String name, List<String> aliases) {
        return new RoomAgentProfile(
                id,
                name,
                "测试角色",
                "",
                "",
                aliases,
                List.of("read"),
                DeepSeekModels.V4_FLASH,
                true,
                false,
                "2026-01-01T00:00:00Z",
                "2026-01-01T00:00:00Z"
        );
    }
}
