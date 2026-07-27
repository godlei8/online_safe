package com.godlei.onlinesafe.vault.application;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

public final class VaultRecordSupport {

    private VaultRecordSupport() {
    }

    public static void requireUuid(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "id 不能为空");
        }
        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "id 必须是 UUID");
        }
    }

    public static void requireItemPayload(JsonNode payload) {
        requireObject(payload);
        requireNonBlankText(payload, "name", "记录名称不能为空");
        requireNonBlankText(payload, "platform", "所属平台不能为空");
        if (!payload.path("fields").isArray()) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "fields 必须是数组");
        }
    }

    public static void requireTemplatePayload(JsonNode payload) {
        requireObject(payload);
        requireNonBlankText(payload, "name", "模板名称不能为空");
        if (!payload.path("fields").isArray()) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "fields 必须是数组");
        }
    }

    private static void requireObject(JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "payload 必须是 JSON 对象");
        }
    }

    private static void requireNonBlankText(JsonNode payload, String field, String message) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", message);
        }
    }
}
