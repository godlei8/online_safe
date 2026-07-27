package com.godlei.onlinesafe.datarecovery.web;

import java.util.List;

public record TrashPageResponse(
        List<TrashAssetResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
