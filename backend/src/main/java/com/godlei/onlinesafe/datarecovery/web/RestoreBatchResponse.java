package com.godlei.onlinesafe.datarecovery.web;

public record RestoreBatchResponse(
        int batchNo,
        String status,
        int createdCount,
        int restoredCount,
        int skippedCount,
        int failedCount
) {
}
