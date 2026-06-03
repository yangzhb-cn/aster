package com.aster.app.runtime;

import com.aster.core.agent.AgentLoop;
import com.aster.core.agent.control.AgentRunControl;
import com.aster.core.event.AgentEventBus;
import com.aster.core.event.model.AgentEvent;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent run 调度器。
 *
 * <p>它负责运行状态、follow-up 队列和当前 run 的控制信号。
 * TUI 或未来 Web 入口只调用 submit、steer、stop，不直接管理 AgentLoop 线程。</p>
 */
public class AgentRunCoordinator implements AutoCloseable {
    private final AgentLoop agentLoop;
    private final AgentEventBus eventBus;
    private final ExecutorService executor;
    private final Queue<String> followUps = new ArrayDeque<>();
    private final Object lock = new Object();

    private boolean busy;
    private AgentRunControl currentControl;

    public AgentRunCoordinator(AgentLoop agentLoop, AgentEventBus eventBus) {
        this(agentLoop, eventBus, Executors.newSingleThreadExecutor());
    }

    public AgentRunCoordinator(AgentLoop agentLoop, AgentEventBus eventBus, ExecutorService executor) {
        this.agentLoop = Objects.requireNonNull(agentLoop);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * 提交一条用户输入。空闲时立即启动，忙碌时进入 follow-up 队列。
     */
    public void submit(String userInput) {
        String input = requireText(userInput, "userInput");
        int queueSize = 0;
        boolean queued = false;
        AgentRunControl control = null;
        synchronized (lock) {
            if (busy) {
                followUps.add(input);
                queueSize = followUps.size();
                queued = true;
            } else {
                busy = true;
                control = new AgentRunControl();
                currentControl = control;
            }
        }

        if (queued) {
            eventBus.publish(new AgentEvent.RunQueued(input, queueSize));
            return;
        }
        AgentRunControl firstControl = control;
        executor.submit(() -> drainRuns(input, firstControl));
    }

    /**
     * 给当前 run 写入一条运行中引导。
     */
    public boolean steer(String text) {
        String steerText = requireText(text, "text");
        AgentRunControl control;
        synchronized (lock) {
            control = currentControl;
            if (control == null) {
                return false;
            }
            control.addSteer(steerText);
        }

        eventBus.publish(new AgentEvent.SteerReceived(steerText, control.pendingSteerCount()));
        return true;
    }

    /**
     * 请求当前 run 在安全点停止。
     */
    public boolean stop() {
        AgentRunControl control;
        synchronized (lock) {
            control = currentControl;
            if (control == null) {
                return false;
            }
            control.requestStop();
        }

        eventBus.publish(new AgentEvent.StopRequested());
        return true;
    }

    /**
     * 判断当前是否有正在执行或等待执行的用户输入。
     */
    public boolean isBusy() {
        synchronized (lock) {
            return busy;
        }
    }

    /**
     * 返回当前 follow-up 队列长度。
     */
    public int queuedCount() {
        synchronized (lock) {
            return followUps.size();
        }
    }

    /**
     * 关闭调度线程。
     */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    /**
     * 在单线程里连续消费当前输入和后续 follow-up 队列。
     */
    private void drainRuns(String firstInput, AgentRunControl firstControl) {
        String nextInput = firstInput;
        AgentRunControl control = firstControl;
        while (nextInput != null) {
            runOne(nextInput, control);

            synchronized (lock) {
                currentControl = null;
                nextInput = followUps.poll();
                if (nextInput == null) {
                    busy = false;
                    control = null;
                } else {
                    control = new AgentRunControl();
                    currentControl = control;
                }
            }
        }
    }

    /**
     * 执行单条用户输入。异常已经由 AgentLoop 转成事件，这里只负责不中断队列。
     */
    private void runOne(String userInput, AgentRunControl control) {
        try {
            agentLoop.run(userInput, control);
        } catch (IOException | RuntimeException ignored) {
            // AgentLoop 已经发布 RunFailed。这里继续消费 follow-up 队列。
        }
    }

    /**
     * 校验入口层传入的文本参数。
     */
    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
