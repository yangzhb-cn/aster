package com.aster.core.tool;

import com.aster.llm.text.model.ToolCall;
import com.aster.core.tool.model.ToolResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 并发执行模型请求的多个工具调用。
 *
 * <p>LLM 可以在一条 assistant 消息里返回多个工具调用。
 * 并行执行可以降低延迟，但每个调用仍然必须产出一条匹配的
 * {@code role=tool} 结果，才能保持协议正确。</p>
 */
public class ParallelToolExecutor implements AutoCloseable {
    private final ToolRegistry toolRegistry;
    private final ExecutorService executor;

    public ParallelToolExecutor(ToolRegistry toolRegistry, ExecutorService executor) {
        this.toolRegistry = toolRegistry;
        this.executor = executor;
    }

    public static ParallelToolExecutor fixedPool(ToolRegistry toolRegistry, int threads) {
        return new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(threads));
    }

    /**
     * 并行执行所有调用，并按模型原始调用顺序返回结果。
     */
    public List<ToolResult> executeAll(List<ToolCall> calls) {
        // 模型可能在一条 assistant 消息里发出多个工具调用。
        // 这些调用可以并行执行，但返回列表仍保留模型原始顺序，
        // 这样 session 对话转写更容易阅读和调试。
        List<CompletableFuture<ToolResult>> futures = calls.stream()
                .map(call -> CompletableFuture
                        .supplyAsync(() -> executeOneWithTiming(call), executor)
                        .<ToolResult>exceptionally(error -> ToolResult.error(call.id(), error.getMessage())
                                .withExecutionMetadata(call.function().name(), 0)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 执行单个工具并记录耗时。
     */
    private ToolResult executeOneWithTiming(ToolCall call) {
        long start = System.nanoTime();
        ToolResult result = toolRegistry.execute(call);
        long elapsedMillis = Math.max(0, (System.nanoTime() - start) / 1_000_000);
        return result.withExecutionMetadata(call.function().name(), elapsedMillis);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
