package com.godlei.onlinesafe.audit.web;

import java.time.Instant;

public record SecurityAuditListItemResponse(
        String id,
        Instant occurredAt,
        String category,
        String eventType,
        String eventLabelZh,
        String riskLevel,
        String result,
        String actorType,
        String actorLabel,
        String targetLabel,
        String sourceSummary,
        int occurrenceCount
) {
}
