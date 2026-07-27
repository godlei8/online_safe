package com.godlei.onlinesafe.datarecovery.web;

import java.time.Instant;

public record DataSecuritySummaryResponse(
        EncryptionSummary encryption,
        BackupSummary backup,
        TrashSummary trash
) {
    public record EncryptionSummary(String status, Instant lastCheckedAt) {
    }

    public record BackupSummary(Instant lastSnapshotAt) {
    }

    public record TrashSummary(
            long itemCount,
            long templateCount,
            Instant nearestPurgeAt,
            int retentionDays
    ) {
    }
}
