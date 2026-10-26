package com.eyefollowup.ledger.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 客户端协议与密钥安全测试（本地 HttpServer，不依赖外部网络）。
 */
class LlmClientTest {

    private HttpServer server;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private LlmClient client(String apiKey) {
        return new LlmClient(new ObjectMapper(), true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-model", apiKey, true, 512, 0.2, 5000);
    }

    @Test
    void sendsApiKeyAndParsesContent() throws Exception {
        response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}";
        String content = client("sk-test-secret").chat("sys", "user");

        assertEquals("{\"ok\":true}", content);
        assertEquals("Bearer sk-test-secret", auth.get());
        assertEquals("/v1/chat/completions", path.get());
        assertTrue(body.get().contains("\"model\":\"test-model\""));
        assertTrue(body.get().contains("\"response_format\""));
        assertTrue(body.get().contains("\"max_tokens\":512"));
    }

    @Test
    void omitsAuthorizationWithoutKey() throws Exception {
        response = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}";
        client("").chat("sys", "user");
        assertNull(auth.get(), "本地兼容服务（如 Ollama）不应发送 Authorization 头");
    }

    @Test
    void errorMessageRedactsKey() {
        status = 401;
        response = "{\"error\":\"invalid api key sk-test-secret\"}";
        LlmClient c = client("sk-test-secret");
        Exception e = assertThrows(Exception.class, () -> c.chat("sys", "user"));
        assertFalse(e.getMessage().contains("sk-test-secret"), "异常信息不得泄露密钥");
        assertTrue(e.getMessage().contains("****"));
    }
}
