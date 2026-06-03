package dev.agentmvp.core.stage;

import java.io.IOException;

/**
 * 由多个 Stage 组成的固定流水线。
 *
 * <p>单个 Stage 表示一个必经步骤，StagePipeline 表示一组步骤的稳定顺序。
 * 例如上下文流水线会先加载 session 历史，再执行上下文压缩和协议校验。</p>
 */
public interface StagePipeline<R> {
    /**
     * 流水线的稳定名称，方便事件、日志和教程引用。
     */
    String name();

    /**
     * 执行整条流水线，并返回该流水线的产物。
     */
    R run() throws IOException;
}
