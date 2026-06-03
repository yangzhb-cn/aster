package com.aster.app.tool.developer;

import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * web_search 扩展工具。
 *
 * <p>通过 Tavily Search API 搜索网页；如果未配置 TAVILY_API_KEY，会返回明确错误。</p>
 */
public class WebSearchTool extends AbstractDeveloperTool {
    private static final MediaType JSON = MediaType.parse("application/json");
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final Map<String, String> env;

    public WebSearchTool(Path workingDirectory, ObjectMapper objectMapper, OkHttpClient httpClient) {
        this(workingDirectory, objectMapper, httpClient, System.getenv());
    }

    public WebSearchTool(
            Path workingDirectory,
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            Map<String, String> env
    ) {
        super(workingDirectory);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.env = Map.copyOf(Objects.requireNonNull(env));
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "web_search",
                "WebSearch",
                "使用 Tavily 搜索网页并返回可引用的链接和摘要。需要 TAVILY_API_KEY。",
                objectSchema(
                        Map.of(
                                "query", stringSchema("要搜索的查询"),
                                "allowed_domains", stringArraySchema("仅包含这些 domains 的搜索结果"),
                                "blocked_domains", stringArraySchema("排除这些 domains 的搜索结果")
                        ),
                        List.of("query")
                )
        );
    }

    /**
     * 调用 Tavily 搜索并格式化结果。
     */
    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        String query = requiredString(arguments, "query").trim();
        List<String> allowed = stringList(arguments.get("allowed_domains"));
        List<String> blocked = stringList(arguments.get("blocked_domains"));
        if (!allowed.isEmpty() && !blocked.isEmpty()) {
            return ToolResult.error(call.id(), "allowed_domains 和 blocked_domains 不能同时使用");
        }

        String apiKey = apiKey();
        if (apiKey.isBlank()) {
            return ToolResult.error(call.id(), "未找到 TAVILY_API_KEY。请设置环境变量或在项目根目录/父目录的 .env 中添加 TAVILY_API_KEY。");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("search_depth", "basic");
        body.put("max_results", 5);
        body.put("include_answer", true);
        if (!allowed.isEmpty()) {
            body.put("include_domains", allowed);
        }
        if (!blocked.isEmpty()) {
            body.put("exclude_domains", blocked);
        }

        Request request = new Request.Builder()
                .url("https://api.tavily.com/search")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                return ToolResult.error(call.id(), "Tavily HTTP " + response.code() + ": " + truncate(text, 1_000));
            }
            return ToolResult.text(call.id(), formatResult(query, objectMapper.readTree(text)));
        }
    }

    private String apiKey() {
        String value = env.get("TAVILY_API_KEY");
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        Path current = workingDirectory.toAbsolutePath().normalize();
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        while (current != null) {
            String fromFile = dotenvValue(current.resolve(".env"));
            if (!fromFile.isBlank()) {
                return fromFile;
            }
            if (current.equals(home) || current.equals(current.getParent())) {
                break;
            }
            current = current.getParent();
        }
        return "";
    }

    private String dotenvValue(Path path) {
        if (!Files.exists(path)) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(path)) {
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#") || !text.contains("=")) {
                    continue;
                }
                int index = text.indexOf('=');
                if (!text.substring(0, index).trim().equals("TAVILY_API_KEY")) {
                    continue;
                }
                String value = text.substring(index + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (Exception ignored) {
            // .env 读取失败时继续使用环境变量结果。
        }
        return "";
    }

    private String formatResult(String query, JsonNode root) {
        StringBuilder output = new StringBuilder("Web search results for query: \"").append(query).append("\"\n\n");
        String answer = root.path("answer").asText("");
        if (!answer.isBlank()) {
            output.append("Answer: ").append(answer.strip()).append("\n\n");
        }

        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return output.append("未找到搜索结果。").toString();
        }
        for (int i = 0; i < results.size(); i++) {
            JsonNode result = results.get(i);
            String title = result.path("title").asText(result.path("url").asText("Untitled"));
            String url = result.path("url").asText("");
            String content = result.path("content").asText("");
            output.append(i + 1).append(". [").append(title).append("](").append(url).append(")\n");
            if (!content.isBlank()) {
                output.append("   ").append(content.strip()).append("\n");
            }
            if (result.has("score")) {
                output.append("   score: ").append(result.path("score").asDouble()).append("\n");
            }
            output.append("\n");
        }
        output.append("REMINDER: 使用这些结果回答用户时，请用 markdown 链接列出相关来源。");
        return output.toString().stripTrailing();
    }
}
