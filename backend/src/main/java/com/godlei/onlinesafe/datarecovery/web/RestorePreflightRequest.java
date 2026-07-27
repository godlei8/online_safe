package com.godlei.onlinesafe.datarecovery.web;

import java.util.List;

public record RestorePreflightRequest(
        String sourceBackupId,
        Integer formatVersion,
        List<RestorePreflightEntry> entries
) {
    public record RestorePreflightEntry(
            String type,
            String sourceId,
            boolean deletedInBackup
    ) {
    }
}
