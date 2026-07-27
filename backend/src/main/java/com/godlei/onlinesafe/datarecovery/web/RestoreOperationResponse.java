package com.godlei.onlinesafe.datarecovery.web;

import java.time.Instant;

public record RestoreOperationResponse(
        String operationId,
        String status,
        int totalCount,
        int processedCount,
        int createdCount,
        int restoredCount,
        int skippedCount,
        int failedCount,
        String errorCode,
        Instant expiresAt,
        Instant startedAt,
        Instant finishedAt
) {
}
