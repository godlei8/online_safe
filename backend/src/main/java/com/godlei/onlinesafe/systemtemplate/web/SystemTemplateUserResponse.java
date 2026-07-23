package com.godlei.onlinesafe.systemtemplate.web;

import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

public record SystemTemplateUserResponse(
        String id,
        String name,
        String platform,
        String channel,
        String channelUrl,
        JsonNode fields,
        int sortOrder,
        Instant publishedAt,
        Instant updatedAt
) {
    public static SystemTemplateUserResponse from(SystemTemplate template, ObjectMapper objectMapper) {
        JsonNode fields;
        try {
            JsonNode root = objectMapper.readTree(template.getPayloadJson());
            fields = root.path("fields");
            if (!fields.isArray()) {
                fields = objectMapper.createArrayNode();
            }
        } catch (Exception ex) {
            fields = objectMapper.createArrayNode();
        }
        return new SystemTemplateUserResponse(
                template.getId(),
                template.getName(),
                template.getPlatform(),
                template.getChannel(),
                template.getChannelUrl(),
                fields,
                template.getSortOrder(),
                template.getPublishedAt(),
                template.getUpdatedAt()
        );
    }
}
