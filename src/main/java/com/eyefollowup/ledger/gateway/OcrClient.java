package com.eyefollowup.ledger.gateway;

import com.eyefollowup.ledger.common.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * OCR 服务 HTTP 客户端（Java 11 HttpClient，无额外依赖）。
 */
@Slf4j
@Component
public class OcrClient {

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String baseUrl;
    private final long timeoutMs;

    public OcrClient(ObjectMapper mapper,
                     @Value("${app.ocr.url:http://127.0.0.1:8001}") String baseUrl,
                     @Value("${app.ocr.timeout-ms:120000}") long timeoutMs) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 调用 OCR 识别一张报告图片。
     *
     * @param absolutePath 图片的绝对路径（OCR 服务与后端同机部署）
     */
    public JsonNode analyze(String absolutePath) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("image_path", absolutePath);

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ocr/analyze"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new BizException(502, "OCR 服务返回异常状态：" + response.statusCode());
            }
            JsonNode node = mapper.readTree(response.body());
            if (!node.path("ok").asBoolean(false)) {
                throw new BizException(502, "OCR 识别失败：" + node.path("error").asText("未知错误"));
            }
            return node;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用 OCR 服务失败: {}", e.getMessage());
            throw new BizException(502, "OCR 服务不可用，请确认 ocr_service 已启动（127.0.0.1:8001）");
        }
    }

    /** 健康检查（3 秒超时）。 */
    public boolean healthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ocr/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
