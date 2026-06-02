package dev.agentmvp.tui;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import dev.agentmvp.runtime.AgentRuntime;
import dev.agentmvp.runtime.AgentRuntimeFactory;

/**
 * TUI 入口。
 *
 * <p>项目现在只保留 TUI 入口，不再保留 CLI 入口。
 * 运行方式仍然是 {@code mvn -q exec:java}，exec 插件会启动这个 mainClass。</p>
 */
public final class TuiMain {
    private TuiMain() {
    }

    /**
     * 启动 Lanterna 文本界面。
     */
    public static void main(String[] args) throws Exception {
        DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory()
                // 教学版希望像 Claude Code 一样跑在当前终端里，而不是弹出 Swing 窗口。
                .setForceTextTerminal(true)
                .setPreferTerminalEmulator(false);
        Screen screen = terminalFactory.createScreen();
        AgentRuntime runtime = null;
        AgentTuiWindow window = null;

        try {
            screen.startScreen();
            window = new AgentTuiWindow(screen);

            try {
                runtime = new AgentRuntimeFactory().create(new TuiAgentEventHandler(window));
                window.setRuntime(runtime);
            } catch (Exception e) {
                // 例如缺少 API key 时，也让 TUI 正常打开，用户能在界面里看到原因。
                window.appendSystemLine(e.getMessage());
                window.setStatus("Runtime failed: " + e.getMessage());
            }

            window.run();
        } finally {
            if (runtime != null) {
                runtime.close();
            }
            if (window != null) {
                window.close();
            }
            screen.stopScreen();
        }
    }
}
