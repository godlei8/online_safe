package com.godlei.onlinesafe.vaultimport.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OpenAiCompatibleChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatClient.class);

    private final ImportAiConfigService configService;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatClient(
            ImportAiConfigService configService,
            @Qualifier("importAiRestClientBuilder") RestClient.Builder importAiRestClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.configService = configService;
        this.restClientBuilder = importAiRestClientBuilder;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return configService.isConfigured();
    }

    public String modelName() {
        return configService.model();
    }

    public String providerHint() {
        return configService.providerHint();
    }

    public String chatJson(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new VaultImportException("IMPORT_AI_UNAVAILABLE", "未配置智能导入 AI");
        }
        String endpoint = AiHttpSupport.chatCompletionsUrl(configService.baseUrl());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", configService.model());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        // 不强制 response_format：多数兼容网关不支持 json_object，靠提示词约束即可
        body.put("temperature", 0);

        int attempts = Math.max(1, configService.maxRetries() + 1);
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                String raw = restClientBuilder.build()
                        .post()
                        .uri(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + configService.apiKey())
                        .body(objectMapper.writeValueAsString(body))
                        .retrieve()
                        .body(String.class);
                if (raw == null || raw.isBlank()) {
                    throw new VaultImportException("IMPORT_AI_UNAVAILABLE", "AI 返回为空");
                }
                JsonNode response = objectMapper.readTree(raw);
                JsonNode choices = response.get("choices");
                if (choices == null || !choices.isArray() || choices.isEmpty()) {
                    throw new VaultImportException("IMPORT_AI_UNAVAILABLE", "AI 返回缺少 choices");
                }
                JsonNode message = choices.get(0).path("message");
                String content = AiHttpSupport.extractMessageContent(message);
                if (content == null || content.isBlank()) {
                    throw new VaultImportException("IMPORT_AI_UNAVAILABLE", "AI 返回内容为空");
                }
                return content;
            } catch (VaultImportException exception) {
                throw exception;
            } catch (RestClientResponseException exception) {
                last = exception;
                log.warn(
                        "AI chat HTTP {} attempt {}/{}: {}",
                        exception.getStatusCode().value(),
                        i + 1,
                        attempts,
                        AiHttpSupport.summarizeHttpBody(exception.getResponseBodyAsString())
                );
            } catch (RuntimeException exception) {
                last = exception;
                log.warn("AI chat failed attempt {}/{}: {}", i + 1, attempts, exception.toString());
            }
        }
        throw new VaultImportException("IMPORT_AI_UNAVAILABLE", AiHttpSupport.toUserMessage(last));
    }
}
