package com.aster.app.tool.developer;

import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.ToolCall;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * web_fetch 扩展工具。
 *
 * <p>抓取指定 URL 的正文并做轻量 HTML 转文本，不在工具内部调用模型总结。</p>
 */
public class WebFetchTool extends AbstractDeveloperTool {
    private static final int MAX_CHARS = 10_000;
    private final OkHttpClient httpClient;

    public WebFetchTool(Path workingDirectory, OkHttpClient httpClient) {
        super(workingDirectory);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "web_fetch",
                "WebFetch",
                "获取指定 URL 的网页内容，返回简化后的文本片段。已有具体 URL 时使用。",
                objectSchema(
                        Map.of(
                                "url", stringSchema("要获取内容的完整 URL"),
                                "prompt", stringSchema("希望从页面内容中关注的信息；工具只抓取文本，不做模型总结")
                        ),
                        List.of("url")
                )
        );
    }

    /**
     * 读取 URL 并返回截断后的文本内容。
     */
    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        String url = requiredString(arguments, "url").trim();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Aster/0.1")
                .header("Accept", "text/html,text/plain,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                return ToolResult.error(call.id(), "HTTP " + response.code() + " for " + url + "\n" + truncate(body, 1_000));
            }

            String contentType = response.header("Content-Type", "");
            String text = looksLikeHtml(contentType, body) ? htmlToText(body) : body.strip();
            return ToolResult.text(call.id(), """
                    URL: %s
                    HTTP: %s

                    %s
                    """.formatted(response.request().url(), response.code(), truncate(text, MAX_CHARS)).stripTrailing());
        }
    }

    private boolean looksLikeHtml(String contentType, String body) {
        return contentType.toLowerCase().contains("html") || body.stripLeading().startsWith("<");
    }

    private String htmlToText(String html) {
        return decodeEntities(html)
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<noscript\\b[^>]*>.*?</noscript>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|tr)>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private String decodeEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
