package com.godlei.onlinesafe.audit.web;

public record SecurityAuditStatsResponse(
        long loginFailuresLast24Hours,
        long highRiskLast7Days,
        long adminActionsLast7Days,
        long blockedLast24Hours
) {
}
