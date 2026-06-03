package com.aster;

import com.aster.core.context.TranscriptSummarizer;
import com.aster.llm.model.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上下文摘要 prompt 注入测试。
 */
class TranscriptSummarizerTest {
    /**
     * 验证摘要器会把外部传入的摘要 prompt 纳入摘要输入。
     */
    @Test
    void includesConfiguredSummaryPromptInMvpSummaryInput() {
        TranscriptSummarizer summarizer = new TranscriptSummarizer(
                "请保留文件路径和测试结果。",
                1_000
        );

        String summary = summarizer.summarize(List.of(
                Message.user("修改 /tmp/app.java"),
                Message.assistant("测试通过")
        ));

        assertTrue(summary.contains("请保留文件路径和测试结果。"));
        assertTrue(summary.contains("## 历史对话转写"));
        assertTrue(summary.contains("user: 修改 /tmp/app.java"));
        assertTrue(summary.contains("assistant: 测试通过"));
    }
}
