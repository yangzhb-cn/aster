package com.aster.app.plan;

import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanTask;
import com.aster.app.plan.model.PlanTaskType;
import com.aster.llm.StreamingChatClient;
import com.aster.llm.model.ChatRequest;
import com.aster.llm.model.Message;
import com.aster.llm.model.ProviderStreamEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 动态 Plan DAG 生成 Agent。
 *
 * <p>它只负责把用户目标拆成 JSON DAG，并在 Java 侧校验依赖是否存在、
 * id 是否重复、DAG 是否无环。真正执行仍交给 PlanRunner。</p>
 */
public class PlanPlannerAgent {
    private static final int MAX_TASKS = 12;

    private final ObjectMapper objectMapper;
    private final StreamingChatClient streamingChatClient;
    private final String systemPrompt;
    private final String model;

    public PlanPlannerAgent(
            ObjectMapper objectMapper,
            StreamingChatClient streamingChatClient,
            String systemPrompt,
            String model
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
        this.systemPrompt = Objects.requireNonNull(systemPrompt);
        this.model = requireText(model, "model");
    }

    /**
     * 让模型为用户目标生成一份动态 DAG。
     */
    public ExecutionPlan createPlan(String task) throws IOException {
        String input = requireText(task, "task");
        StringBuilder response = new StringBuilder();
        ChatRequest request = ChatRequest.streaming(
                model,
                List.of(Message.system(systemPrompt), Message.user(input)),
                List.of(),
                null,
                false,
                null
        );
        streamingChatClient.stream(request, event -> {
            if (event instanceof ProviderStreamEvent.TextDelta delta) {
                response.append(delta.text());
            }
        });
        return parsePlan(objectMapper, input, response.toString());
    }

    /**
     * 解析模型输出的 JSON，并返回经过依赖校验的 ExecutionPlan。
     */
    public static ExecutionPlan parsePlan(ObjectMapper objectMapper, String fallbackTask, String raw) throws IOException {
        Objects.requireNonNull(objectMapper);
        String json = extractJson(raw);
        JsonNode root = objectMapper.readTree(json);
        String task = text(root.get("task"));
        if (task.isBlank()) {
            task = requireText(fallbackTask, "fallbackTask");
        }

        JsonNode tasksNode = root.get("tasks");
        if (tasksNode == null || !tasksNode.isArray() || tasksNode.isEmpty()) {
            throw new IllegalArgumentException("plan tasks are required");
        }
        if (tasksNode.size() > MAX_TASKS) {
            throw new IllegalArgumentException("plan tasks too many: " + tasksNode.size());
        }

        List<PlanTask> tasks = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode taskNode : tasksNode) {
            String id = text(taskNode.get("id"));
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate plan task id: " + id);
            }
            tasks.add(new PlanTask(
                    id,
                    text(taskNode.get("description")),
                    parseType(text(taskNode.get("type"))),
                    dependencies(taskNode.get("dependencies"))
            ));
        }

        ExecutionPlan plan = new ExecutionPlan(task, tasks);
        validateDependencies(plan);
        return plan;
    }

    private static void validateDependencies(ExecutionPlan plan) {
        Set<String> ids = new HashSet<>();
        for (PlanTask task : plan.tasks()) {
            ids.add(task.id());
        }
        for (PlanTask task : plan.tasks()) {
            for (String dependencyId : task.dependencies()) {
                if (task.id().equals(dependencyId)) {
                    throw new IllegalArgumentException("plan task cannot depend on itself: " + task.id());
                }
                if (!ids.contains(dependencyId)) {
                    throw new IllegalArgumentException("unknown dependency " + dependencyId + " for task " + task.id());
                }
            }
        }
        assertAcyclic(plan);
    }

    private static void assertAcyclic(ExecutionPlan plan) {
        Map<String, Integer> state = new HashMap<>();
        for (PlanTask task : plan.tasks()) {
            visit(task, plan, state);
        }
    }

    private static void visit(PlanTask task, ExecutionPlan plan, Map<String, Integer> state) {
        int current = state.getOrDefault(task.id(), 0);
        if (current == 1) {
            throw new IllegalArgumentException("plan dependency cycle contains task: " + task.id());
        }
        if (current == 2) {
            return;
        }
        state.put(task.id(), 1);
        for (String dependencyId : task.dependencies()) {
            visit(plan.task(dependencyId), plan, state);
        }
        state.put(task.id(), 2);
    }

    private static PlanTaskType parseType(String value) {
        try {
            return PlanTaskType.valueOf(requireText(value, "type").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown plan task type: " + value, e);
        }
    }

    private static List<String> dependencies(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("dependencies must be array");
        }
        List<String> dependencies = new ArrayList<>();
        for (JsonNode dependency : node) {
            dependencies.add(requireText(text(dependency), "dependency"));
        }
        return dependencies;
    }

    private static String extractJson(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                value = value.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("planner did not return JSON object");
        }
        return value.substring(start, end + 1);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("").trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
