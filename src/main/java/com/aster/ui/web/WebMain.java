package com.aster.ui.web;

import com.aster.app.runtime.AgentRuntimeFactory;
import com.aster.core.session.SessionCatalog;

/**
 * Aster Web 入口。
 *
 * <p>默认监听 8080 端口，session 默认使用 default。
 * 可以通过 ASTER_WEB_PORT 和 ASTER_SESSION 环境变量覆盖。</p>
 */
public final class WebMain {
    private WebMain() {
    }

    /**
     * 启动 Web Chat 服务。
     */
    public static void main(String[] args) throws Exception {
        int port = readPort();
        String sessionName = readSessionName();
        WebServer server = new WebServer(port, new AgentRuntimeFactory(), sessionName);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("Aster Web listening on http://localhost:" + server.port());
        Thread.currentThread().join();
    }

    private static int readPort() {
        String raw = System.getenv("ASTER_WEB_PORT");
        if (raw == null || raw.isBlank()) {
            return 8080;
        }
        return Integer.parseInt(raw.trim());
    }

    private static String readSessionName() {
        String raw = System.getenv("ASTER_SESSION");
        if (raw == null || raw.isBlank()) {
            return SessionCatalog.DEFAULT_SESSION;
        }
        SessionCatalog.requireValidName(raw.trim());
        return raw.trim();
    }
}
