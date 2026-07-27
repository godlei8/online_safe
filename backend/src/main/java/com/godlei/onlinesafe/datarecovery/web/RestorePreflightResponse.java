package com.godlei.onlinesafe.datarecovery.web;

import java.util.List;

public record RestorePreflightResponse(
        int createCount,
        int restoreDeletedCount,
        int skipActiveConflictCount,
        int reassignIdCount,
        int invalidCount,
        int trashInBackupCount,
        List<RestorePreflightDecision> decisions
) {
    public record RestorePreflightDecision(
            String type,
            String sourceId,
            String action
    ) {
    }
}
