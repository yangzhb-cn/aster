package com.aster.core.hook;

import java.io.IOException;

/**
 * 单个 Hook 处理器。
 *
 * <p>C 是输入上下文类型，R 是返回结果类型。不同 HookPoint 可以有不同的上下文和返回值：
 * 请求前 Hook 返回改写后的上下文，工具审批 Hook 返回允许/拒绝决策，afterRun Hook 返回 Void。</p>
 */
@FunctionalInterface
public interface HookHandler<C, R> {
    /**
     * 执行 Hook 逻辑。
     */
    R handle(C context) throws IOException;
}
