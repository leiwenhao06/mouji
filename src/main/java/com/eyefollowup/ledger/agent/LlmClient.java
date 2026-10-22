package com.eyefollowup.ledger.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Ollama 本地大模型 HTTP 客户端。
 * 关键配置：think=false（推理模型关闭思维链，否则 content 为空）、num_ctx=8192。
 * 不可用时由上层透明降级到规则草稿。
 */
@Slf4j
@Component
public class LlmClient {

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;
    private final boolean think;
    private final int numCtx;
    private final int maxTokens;
    private final double temperature;
    private final long timeoutMs;

    private volatile boolean lastHealthy = false;
    private volatile long lastCheckAt = 0;

    public LlmClient(ObjectMapper mapper,
                     @Value("${app.agent.llm.enabled:false}") boolean enabled,
                     @Value("${app.agent.llm.base-url:http://127.0.0.1:11434}") String baseUrl,
                     @Value("${app.agent.llm.model:qwen3:4b}") String model,
                     @Value("${app.agent.llm.think:false}") boolean think,
                     @Value("${app.agent.llm.num-ctx:8192}") int numCtx,
                     @Value("${app.agent.llm.max-tokens:1600}") int maxTokens,
                     @Value("${app.agent.llm.temperature:0.3}") double temperature,
                     @Value("${app.agent.llm.timeout-ms:120000}") long timeoutMs) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.think = think;
        this.numCtx = numCtx;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 健康检查缓存 10 秒，避免每次请求都探测。 */
    public boolean healthy() {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < 10_000) {
            return lastHealthy;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - lastCheckAt < 10_000) {
                return lastHealthy;
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                        .timeout(Duration.ofSeconds(2)).GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                lastHealthy = resp.statusCode() == 200;
            } catch (Exception e) {
                lastHealthy = false;
            }
            lastCheckAt = System.currentTimeMillis();
            return lastHealthy;
        }
    }

    /**
     * 同步调用 /api/chat，返回 message.content。
     */
    public String chat(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("stream", false);
        root.put("format", "json");
        root.put("think", think);

        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        ObjectNode options = root.putObject("options");
        options.put("temperature", temperature);
        options.put("num_ctx", numCtx);
        options.put("num_predict", maxTokens);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(root), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("LLM 返回状态 " + resp.statusCode());
        }
        JsonNode node = mapper.readTree(resp.body());
        String content = node.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("LLM 返回内容为空（若使用推理模型，请确认 think=false）");
        }
        return content;
    }
}
