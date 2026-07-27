package com.godlei.onlinesafe.datarecovery.web;

import java.time.Instant;

public record TrashAssetResponse(
        String id,
        String type,
        String name,
        String summary,
        Instant deletedAt,
        int remainingDays
) {
}
