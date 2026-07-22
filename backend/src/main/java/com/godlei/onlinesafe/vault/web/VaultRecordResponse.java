package com.godlei.onlinesafe.vault.web;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record VaultRecordResponse(
        String id,
        JsonNode payload,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
