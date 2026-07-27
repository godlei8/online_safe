package com.godlei.onlinesafe.datarecovery.web;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record RestoreBatchRequest(
        boolean includeTrashAssets,
        List<RestoreBatchEntry> entries
) {
    public record RestoreBatchEntry(
            String type,
            String sourceId,
            JsonNode payload,
            boolean deletedInBackup,
            String createdAt,
            String updatedAt
    ) {
    }
}
