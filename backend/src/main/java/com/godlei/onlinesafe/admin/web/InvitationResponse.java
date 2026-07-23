package com.godlei.onlinesafe.admin.web;

import java.time.Instant;

public record InvitationResponse(
        String id,
        String codeHint,
        /** 用途类型，如 USER_REGISTRATION */
        String purpose,
        /** 使用方式：SINGLE / MULTI */
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
