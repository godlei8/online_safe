package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.settings.application.SystemSettingService;
import com.godlei.onlinesafe.vaultimport.web.AiConnectivityTestRequest;
import com.godlei.onlinesafe.vaultimport.web.AiConnectivityTestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AiConnectivityProbeService {

    private static final Logger log = LoggerFactory.getLogger(AiConnectivityProbeService.class);
    private static final String SECRET_MASK = SystemSettingService.SECRET_MASK;

    private final ImportAiConfigService configService;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public AiConnectivityProbeService(
            ImportAiConfigService configService,
            @Qualifier("importAiRestClientBuilder") RestClient.Builder importAiRestClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.configService = configService;
        this.restClientBuilder = importAiRestClientBuilder;
        this.objectMapper = objectMapper;
    }

    public AiConnectivityTestResponse probe(AiConnectivityTestRequest request) {
        String baseUrl = firstNonBlank(request == null ? null : request.baseUrl(), configService.baseUrl());
        String model = firstNonBlank(request == null ? null : request.model(), configService.model());
        String apiKey = resolveApiKey(request == null ? null : request.apiKey());

        if (baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) {
            return AiConnectivityTestResponse.failure(
                    0,
                    model,
                    "请先填写模型 API 根地址、模型名称与 API Key（或先保存后再测）"
            );
        }

        String endpoint = AiHttpSupport.chatCompletionsUrl(baseUrl);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", "Reply with the single word OK");
        // 思考链模型会先消耗 token；过小会导致 content 为空被误判失败
        body.put("max_tokens", 256);
        body.put("temperature", 0);

        long started = System.nanoTime();
        try {
            String raw = restClientBuilder.build()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            long latencyMs = elapsedMs(started);
            if (raw == null || raw.isBlank()) {
                return AiConnectivityTestResponse.failure(latencyMs, model, "供应商返回为空");
            }
            JsonNode response = objectMapper.readTree(raw);
            JsonNode choices = response.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return AiConnectivityTestResponse.failure(
                        latencyMs,
                        model,
                        "供应商返回缺少 choices，请确认 Base URL 指向 OpenAI 兼容的 /v1 根路径"
                );
            }
            JsonNode message = choices.get(0).path("message");
            String content = AiHttpSupport.extractMessageContent(message);
            String preview = content.length() > 80 ? content.substring(0, 80) + "…" : content;
            if (preview.isBlank()) {
                String finish = choices.get(0).path("finish_reason").asText("");
                return AiConnectivityTestResponse.failure(
                        latencyMs,
                        model,
                        "供应商返回内容为空"
                                + (finish.isBlank() ? "" : "（finish_reason=" + finish + "）")
                                + "。请确认模型名正确；Base URL 填到 /v1（DeepSeek 可用 https://api.deepseek.com 或 …/v1）"
                );
            }
            return AiConnectivityTestResponse.success(latencyMs, model, preview);
        } catch (RestClientResponseException exception) {
            long latencyMs = elapsedMs(started);
            String message = "HTTP "
                    + exception.getStatusCode().value()
                    + "："
                    + AiHttpSupport.summarizeHttpBody(exception.getResponseBodyAsString());
            log.warn("AI connectivity probe failed endpoint={} {}", endpoint, message);
            return AiConnectivityTestResponse.failure(latencyMs, model, message);
        } catch (RuntimeException exception) {
            long latencyMs = elapsedMs(started);
            String message = AiHttpSupport.toUserMessage(exception);
            log.warn("AI connectivity probe failed endpoint={} {}", endpoint, message);
            return AiConnectivityTestResponse.failure(latencyMs, model, message);
        }
    }

    private String resolveApiKey(String requested) {
        if (requested == null) {
            return configService.apiKey();
        }
        String trimmed = requested.trim();
        if (trimmed.isBlank() || SECRET_MASK.equals(trimmed)) {
            return configService.apiKey();
        }
        return trimmed;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.trim().isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
