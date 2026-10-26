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
 * OpenAI 兼容的大模型 HTTP 客户端（DeepSeek / 通义 / Moonshot / 硅基流动 / Ollama 均可）。
 *
 * 安全约定：
 * - API Key 只从环境变量 LLM_API_KEY 注入，绝不写入代码、配置文件或日志；
 * - 日志只打印"密钥已配置/未配置"，异常信息在抛出前会把密钥替换为 ****；
 * - 未配置密钥或调用失败时，上层自动降级为确定性规则草稿。
 */
@Slf4j
@Component
public class LlmClient {

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final boolean enabled;
    private final boolean jsonMode;
    private final int maxTokens;
    private final double temperature;
    private final long timeoutMs;

    private volatile boolean lastHealthy = false;
    private volatile long lastCheckAt = 0;

    public LlmClient(ObjectMapper mapper,
                     @Value("${app.agent.llm.enabled:false}") boolean enabled,
                     @Value("${app.agent.llm.base-url:https://api.deepseek.com/v1}") String baseUrl,
                     @Value("${app.agent.llm.model:deepseek-chat}") String model,
                     @Value("${app.agent.llm.api-key:}") String apiKey,
                     @Value("${app.agent.llm.json-mode:true}") boolean jsonMode,
                     @Value("${app.agent.llm.max-tokens:1600}") int maxTokens,
                     @Value("${app.agent.llm.temperature:0.3}") double temperature,
                     @Value("${app.agent.llm.timeout-ms:60000}") long timeoutMs) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.jsonMode = jsonMode;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        if (enabled) {
            log.info("LLM 已启用：baseUrl={}，model={}，apiKey={}", this.baseUrl, this.model,
                    hasKey() ? "已配置" : "未配置（无密钥时仅可连接本地兼容服务）");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean hasKey() {
        return !apiKey.isEmpty();
    }

    /**
     * 健康状态缓存 10 秒。
     * 配置了密钥时直接视为可用（由调用失败触发降级，避免每次探测消耗配额）；
     * 未配置密钥时（如本地 Ollama）探测 /models。
     */
    public boolean healthy() {
        if (!enabled) {
            return false;
        }
        if (hasKey()) {
            return true;
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
                HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                        .timeout(Duration.ofSeconds(3)).GET().build();
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
     * 同步调用 /chat/completions，返回 choices[0].message.content。
     * 密钥仅用于 Authorization 头，不进入请求体、日志或异常文本。
     */
    public String chat(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("stream", false);
        root.put("temperature", temperature);
        root.put("max_tokens", maxTokens);
        if (jsonMode) {
            root.putObject("response_format").put("type", "json_object");
        }

        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(root), StandardCharsets.UTF_8));
        if (hasKey()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> resp = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("LLM 返回状态 " + resp.statusCode() + "：" + sanitize(resp.body()));
        }
        JsonNode node = mapper.readTree(resp.body());
        String content = node.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("LLM 返回内容为空");
        }
        return content;
    }

    /** 异常信息脱敏：移除可能回显的密钥，并截断响应体。 */
    private String sanitize(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String s = hasKey() ? body.replace(apiKey, "****") : body;
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
