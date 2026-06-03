package com.aster.ui.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Web SSE 客户端注册表。
 *
 * <p>每个浏览器 EventSource 连接会注册成一个 Client。Agent 事件到来时，
 * WebAgentEventHandler 会把事件广播给所有仍然可写的客户端。</p>
 */
public class WebSseClientRegistry implements AutoCloseable {
    private final List<Client> clients = new CopyOnWriteArrayList<>();

    /**
     * 注册一个新的 SSE 输出流。
     */
    public Client add(OutputStream outputStream) throws IOException {
        Client client = new Client(outputStream);
        clients.add(client);
        client.send("hello", "{\"status\":\"connected\"}");
        return client;
    }

    /**
     * 广播一条 SSE 事件。
     */
    public void broadcast(String eventName, String json) {
        for (Client client : clients) {
            try {
                client.send(eventName, json);
            } catch (IOException e) {
                clients.remove(client);
                client.close();
            }
        }
    }

    /**
     * 当前连接的浏览器数量。
     */
    public int size() {
        return clients.size();
    }

    @Override
    public void close() {
        for (Client client : clients) {
            client.close();
        }
        clients.clear();
    }

    /**
     * 单个 SSE 客户端连接。
     */
    public static class Client implements AutoCloseable {
        private final OutputStream outputStream;
        private final CountDownLatch closed = new CountDownLatch(1);

        Client(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        /**
         * 写出一条 SSE 事件。
         */
        public synchronized void send(String eventName, String json) throws IOException {
            String frame = "event: " + eventName + "\n"
                    + "data: " + json.replace("\n", "\\n") + "\n\n";
            outputStream.write(frame.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }

        /**
         * 等待连接关闭，保持 HttpExchange 不提前结束。
         */
        public void awaitClosed() throws InterruptedException {
            closed.await();
        }

        @Override
        public void close() {
            try {
                outputStream.close();
            } catch (IOException ignored) {
                // 关闭失败不影响服务退出。
            }
            closed.countDown();
        }
    }
}
