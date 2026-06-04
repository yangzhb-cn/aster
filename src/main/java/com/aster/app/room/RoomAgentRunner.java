package com.aster.app.room;

import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomMembership;
import com.aster.core.agent.AgentLoop;
import com.aster.core.context.ContextBuilder;
import com.aster.core.context.SimpleTokenEstimator;
import com.aster.core.context.TranscriptSummarizer;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.event.AgentEventBus;
import com.aster.core.hook.AgentHookPoints;
import com.aster.core.hook.HookRegistry;
import com.aster.core.session.SessionStore;
import com.aster.core.tool.ParallelToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.llm.StreamingChatClient;
import com.aster.llm.model.OpenAiCompatibleProvider;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 执行单个房间 Agent。
 *
 * <p>它复用 AgentLoop、ContextBuilder、ToolRegistry 和 JSONL session，
 * 但事件总线使用 noop，避免 Web 房间页展示工具调用、reasoning 或 token 流。</p>
 */
public class RoomAgentRunner {
    private static final int MAX_TOOL_ROUNDS = 100;
    private static final int ROOM_CONTEXT_MESSAGES = 80;

    private final OpenAiCompatibleProvider provider;
    private final StreamingChatClient streamingChatClient;
    private final RoomHub roomHub;
    private final RoomAgentPromptStore promptStore;
    private final RoomAgentSessionFactory sessionFactory;
    private final RoomToolRegistryFactory toolRegistryFactory;
    private final RoomPromptBuilder promptBuilder;

    public RoomAgentRunner(
            OpenAiCompatibleProvider provider,
            StreamingChatClient streamingChatClient,
            RoomHub roomHub,
            RoomAgentPromptStore promptStore,
            RoomAgentSessionFactory sessionFactory,
            RoomToolRegistryFactory toolRegistryFactory,
            RoomPromptBuilder promptBuilder
    ) {
        this.provider = Objects.requireNonNull(provider);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
        this.roomHub = Objects.requireNonNull(roomHub);
        this.promptStore = Objects.requireNonNull(promptStore);
        this.sessionFactory = Objects.requireNonNull(sessionFactory);
        this.toolRegistryFactory = Objects.requireNonNull(toolRegistryFactory);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
    }

    /**
     * 让指定 Agent 回复当前房间消息。
     */
    public String run(ChatRoom room, RoomAgentProfile agent, RoomMembership membership, HubMessage triggerMessage) throws IOException {
        List<HubMessage> recentMessages = roomHub.recent(room.roomId(), ROOM_CONTEXT_MESSAGES);
        String agentPrompt = promptStore.read(agent);
        String systemPrompt = promptBuilder.systemPrompt(agent, agentPrompt);
        SessionStore sessionStore = sessionFactory.open(room.roomId(), agent.agentId(), membership.generation(), systemPrompt);
        ToolRegistry toolRegistry = toolRegistryFactory.create(agent);
        HookRegistry hookRegistry = new HookRegistry();
        hookRegistry.register(
                AgentHookPoints.BEFORE_LLM_REQUEST,
                new RoomContextInjectHook(
                        new RoomAgentRunContext(room, agent, triggerMessage, recentMessages),
                        promptBuilder
                )
        );

        try (ParallelToolExecutor toolExecutor = ParallelToolExecutor.fixedPool(toolRegistry, 4)) {
            AgentLoop agentLoop = new AgentLoop(
                    provider,
                    sessionStore,
                    new ContextBuilder(
                            new SimpleTokenEstimator(),
                            new TranscriptSummarizer(4_000),
                            ContextOptions.defaults()
                    ),
                    streamingChatClient,
                    toolRegistry,
                    toolExecutor,
                    hookRegistry,
                    AgentEventBus.noop("room-" + room.roomId() + "-" + agent.agentId()),
                    MAX_TOOL_ROUNDS
            );
            return agentLoop.run(triggerMessage.content());
        }
    }
}
