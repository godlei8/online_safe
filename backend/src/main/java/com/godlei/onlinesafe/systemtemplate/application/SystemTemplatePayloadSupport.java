package com.godlei.onlinesafe.systemtemplate.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.UUID;

final class SystemTemplatePayloadSupport {

    private SystemTemplatePayloadSupport() {
    }

    static String buildPayloadJson(
            ObjectMapper objectMapper,
            String name,
            String platform,
            String channel,
            String channelUrl,
            JsonNode fieldsInput
    ) {
        if (fieldsInput == null || !fieldsInput.isArray()) {
            throw new InvalidSystemTemplateOperationException("VALIDATION_FAILED", "fields 必须是数组");
        }

        ArrayNode normalizedFields = objectMapper.createArrayNode();
        ObjectNode account = null;
        ObjectNode password = null;

        int order = 0;
        for (JsonNode raw : fieldsInput) {
            if (raw == null || !raw.isObject()) {
                continue;
            }
            ObjectNode field = normalizeField(objectMapper, raw, order++);
            String systemKey = textOrEmpty(field.get("systemKey"));
            if ("account".equals(systemKey)) {
                account = field;
            } else if ("password".equals(systemKey)) {
                password = field;
            } else {
                normalizedFields.add(field);
            }
        }

        if (account == null) {
            account = defaultAccount(objectMapper);
        }
        if (password == null) {
            password = defaultPassword(objectMapper);
        }

        ArrayNode finalFields = objectMapper.createArrayNode();
        account.put("order", 0);
        password.put("order", 1);
        finalFields.add(account);
        finalFields.add(password);
        int next = 2;
        for (JsonNode field : normalizedFields) {
            ((ObjectNode) field).put("order", next++);
            finalFields.add(field);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("platform", platform);
        payload.put("channel", channel == null ? "" : channel);
        payload.put("channelUrl", channelUrl == null ? "" : channelUrl);
        payload.set("fields", finalFields);
        return payload.toString();
    }

    static JsonNode parseFields(ObjectMapper objectMapper, String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode fields = root.path("fields");
            return fields.isArray() ? fields : objectMapper.createArrayNode();
        } catch (Exception ex) {
            throw new InvalidSystemTemplateOperationException("VALIDATION_FAILED", "模板内容损坏");
        }
    }

    private static ObjectNode normalizeField(ObjectMapper objectMapper, JsonNode raw, int fallbackOrder) {
        ObjectNode field = objectMapper.createObjectNode();
        String id = textOrEmpty(raw.get("id"));
        if (id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        String name = textOrEmpty(raw.get("name"));
        if (name.isBlank()) {
            name = "字段";
        }
        String type = textOrEmpty(raw.get("type")).toUpperCase(Locale.ROOT);
        if (!isAllowedType(type)) {
            type = "TEXT";
        }
        String systemKey = textOrEmpty(raw.get("systemKey"));
        if ("password".equals(systemKey)) {
            type = "PASSWORD";
            name = "密码";
        } else if ("account".equals(systemKey)) {
            if (!"EMAIL".equals(type) && !"PHONE".equals(type)) {
                type = "TEXT";
            }
            name = "账号";
        } else {
            systemKey = "";
        }

        field.put("id", id);
        field.put("name", name);
        field.put("type", type);
        field.put("value", "");
        field.put("required", raw.path("required").asBoolean(false) || "password".equals(systemKey) || "account".equals(systemKey));
        field.put("sensitive", raw.path("sensitive").asBoolean(false) || "PASSWORD".equals(type));
        field.put("copyable", raw.path("copyable").asBoolean(true));
        field.put("hint", textOrEmpty(raw.get("hint")));
        field.put("order", raw.path("order").asInt(fallbackOrder));
        if (!systemKey.isBlank()) {
            field.put("systemKey", systemKey);
        }
        return field;
    }

    private static ObjectNode defaultAccount(ObjectMapper objectMapper) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("id", UUID.randomUUID().toString());
        field.put("name", "账号");
        field.put("type", "TEXT");
        field.put("value", "");
        field.put("required", true);
        field.put("sensitive", false);
        field.put("copyable", true);
        field.put("hint", "");
        field.put("order", 0);
        field.put("systemKey", "account");
        return field;
    }

    private static ObjectNode defaultPassword(ObjectMapper objectMapper) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("id", UUID.randomUUID().toString());
        field.put("name", "密码");
        field.put("type", "PASSWORD");
        field.put("value", "");
        field.put("required", true);
        field.put("sensitive", true);
        field.put("copyable", true);
        field.put("hint", "");
        field.put("order", 1);
        field.put("systemKey", "password");
        return field;
    }

    private static boolean isAllowedType(String type) {
        return "TEXT".equals(type)
                || "PASSWORD".equals(type)
                || "EMAIL".equals(type)
                || "URL".equals(type)
                || "PHONE".equals(type);
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull() || !node.isString()) {
            return "";
        }
        return node.asString().trim();
    }
}
