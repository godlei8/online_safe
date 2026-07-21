package com.godlei.onlinesafe.admin.web;

import java.time.Instant;

public record InvitationResponse(
        String id,
        String codeHint,
        String type,
        int usedCount,
        int maxUses,
        Instant expiresAt,
        String status,
        String creatorUsername,
        Instant lastUsedAt,
        String note,
        Instant createdAt
) {
}
