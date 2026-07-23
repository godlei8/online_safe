package com.godlei.onlinesafe.systemtemplate.web;

import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplate;
import com.godlei.onlinesafe.systemtemplate.domain.SystemTemplateStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

public record SystemTemplateAdminResponse(
        String id,
        String name,
        String platform,
        String channel,
        String channelUrl,
        JsonNode fields,
        SystemTemplateStatus status,
        int sortOrder,
        String createdByAdminId,
        String updatedByAdminId,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long revision
) {
    public static SystemTemplateAdminResponse from(SystemTemplate template, ObjectMapper objectMapper) {
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
        return new SystemTemplateAdminResponse(
                template.getId(),
                template.getName(),
                template.getPlatform(),
                template.getChannel(),
                template.getChannelUrl(),
                fields,
                template.getStatus(),
                template.getSortOrder(),
                template.getCreatedByAdminId(),
                template.getUpdatedByAdminId(),
                template.getPublishedAt(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getVersion()
        );
    }
}
