package com.aster.app.rag;

import com.aster.app.rag.model.RagHit;
import com.aster.app.rag.model.RagSessionRecord;

import java.util.List;
import java.util.Locale;

/**
 * RAG 问答 prompt 构造器。
 */
public class RagPromptBuilder {
    private static final int MAX_HISTORY_RECORDS = 8;
    private static final int MAX_HIT_CHARS = 1600;

    /**
     * 拼出 RAG 用户消息。
     */
    public String buildUserPrompt(String question, List<RagHit> hits, List<RagSessionRecord> history) {
        return """
                ## 最近 RAG 对话

                %s

                ## 检索到的知识库片段

                %s

                ## 用户问题

                %s
                """.formatted(renderHistory(history), renderHits(hits), question == null ? "" : question.strip());
    }

    private String renderHistory(List<RagSessionRecord> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        List<RagSessionRecord> recent = history.size() <= MAX_HISTORY_RECORDS
                ? history
                : history.subList(history.size() - MAX_HISTORY_RECORDS, history.size());
        StringBuilder output = new StringBuilder();
        for (RagSessionRecord record : recent) {
            output.append(record.type()).append(": ")
                    .append(record.content() == null ? "" : record.content().strip())
                    .append("\n");
        }
        return output.toString().strip();
    }

    private String renderHits(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "没有检索到相关片段。";
        }
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            RagHit hit = hits.get(i);
            output.append("### 片段 ").append(i + 1)
                    .append("\n来源：").append(hit.sourceName())
                    .append(" / chunk ").append(hit.chunkIndex())
                    .append(" / score ").append(String.format(Locale.ROOT, "%.4f", hit.score()))
                    .append("\n\n")
                    .append(limit(hit.text()))
                    .append("\n\n");
        }
        return output.toString().strip();
    }

    private String limit(String text) {
        String value = text == null ? "" : text.strip();
        return value.length() <= MAX_HIT_CHARS ? value : value.substring(0, MAX_HIT_CHARS) + "\n...（片段截断）";
    }
}
