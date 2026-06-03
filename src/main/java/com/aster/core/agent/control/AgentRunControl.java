package com.aster.core.agent.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次 Agent run 的控制信号。
 *
 * <p>它只保存当前 run 内的运行控制状态，不负责调度后续输入。
 * follow-up 排队属于 app/runtime，AgentLoop 只读取这里的 stop 和 steer 信号。</p>
 */
public class AgentRunControl {
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final Queue<String> steerInbox = new ConcurrentLinkedQueue<>();

    /**
     * 请求当前 run 在下一个安全点停止。
     */
    public void requestStop() {
        stopRequested.set(true);
    }

    /**
     * 判断用户是否已经请求停止当前 run。
     */
    public boolean stopRequested() {
        return stopRequested.get();
    }

    /**
     * 写入一条运行中引导消息，等待下一次 LLM 请求前被 Hook 消费。
     */
    public void addSteer(String text) {
        String value = Objects.requireNonNull(text).trim();
        if (!value.isEmpty()) {
            steerInbox.add(value);
        }
    }

    /**
     * 返回当前待处理引导数量。
     */
    public int pendingSteerCount() {
        return steerInbox.size();
    }

    /**
     * 一次性取出所有待处理引导消息。
     */
    public List<String> drainSteers() {
        List<String> steers = new ArrayList<>();
        String steer;
        while ((steer = steerInbox.poll()) != null) {
            steers.add(steer);
        }
        return List.copyOf(steers);
    }
}
