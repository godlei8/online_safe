package com.godlei.onlinesafe.vaultimport.application;

import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

final class AiHttpSupport {

    private AiHttpSupport() {
    }

    /**
     * 规范化为「API 根」：去掉误粘贴的 /chat/completions，保留 /v1。
     */
    static String normalizeBaseUrl(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
        if (normalized.endsWith("/chat/completions")) {
            normalized = trimTrailingSlash(
                    normalized.substring(0, normalized.length() - "/chat/completions".length())
            );
        }
        return normalized;
    }

    static String chatCompletionsUrl(String baseUrl) {
        String root = normalizeBaseUrl(baseUrl);
        if (root.isBlank()) {
            return "";
        }
        return root + "/chat/completions";
    }

    static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    static String extractMessageContent(JsonNode message) {
        if (message == null || message.isMissingNode() || message.isNull()) {
            return "";
        }
        String content = readContentNode(message.get("content"));
        if (!content.isBlank()) {
            return content;
        }
        // DeepSeek reasoner 等：正文可能在 reasoning_content，content 为空
        return readContentNode(message.get("reasoning_content"));
    }

    private static String readContentNode(JsonNode content) {
        if (content == null || content.isNull() || content.isMissingNode()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("").trim();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : content) {
                if (part == null) {
                    continue;
                }
                if (part.isTextual()) {
                    builder.append(part.asText());
                } else if (part.hasNonNull("text")) {
                    builder.append(part.path("text").asText(""));
                }
            }
            return builder.toString().trim();
        }
        return content.asText("").trim();
    }

    static String summarizeHttpBody(String body) {
        if (body == null || body.isBlank()) {
            return "无响应体";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() > 200) {
            return compact.substring(0, 200) + "…";
        }
        return compact;
    }

    static String toUserMessage(Throwable exception) {
        if (exception == null) {
            return "AI 调用失败";
        }
        if (exception instanceof RestClientResponseException responseException) {
            return "AI 调用失败（HTTP "
                    + responseException.getStatusCode().value()
                    + "）："
                    + summarizeHttpBody(responseException.getResponseBodyAsString());
        }
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = root.getClass().getSimpleName();
        }
        if (detail.length() > 200) {
            detail = detail.substring(0, 200) + "…";
        }
        return "AI 调用失败：" + detail;
    }
}
