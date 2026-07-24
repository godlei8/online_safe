package com.godlei.onlinesafe.audit.web;

import java.time.Instant;
import java.util.Map;

public record SecurityAuditDetailResponse(
        String id,
        Instant occurredAt,
        String category,
        String eventType,
        String eventLabelZh,
        String riskLevel,
        String result,
        String actorType,
        String actorLabel,
        String actorId,
        String targetLabel,
        String targetType,
        String targetId,
        String errorCode,
        String requestId,
        String routeTemplate,
        String httpMethod,
        String ipMasked,
        String browserFamily,
        String osFamily,
        String deviceType,
        int occurrenceCount,
        Map<String, Object> metadata
) {
}
