package com.godlei.onlinesafe.admin.web;

import com.godlei.onlinesafe.auth.domain.AppUserStatus;

import java.time.Instant;

public record ManagedUserResponse(
        String id,
        String username,
        String maskedPhone,
        AppUserStatus status,
        Instant createdAt,
        Instant lastLoginAt,
        int activeSessionCount,
        Long cipherStorageBytes,
        Integer recordCount
) {
}
