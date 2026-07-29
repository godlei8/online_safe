package com.godlei.onlinesafe.vaultimport.web;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record ImportSessionResponse(
        String sessionId,
        String status,
        String progressMessage,
        String errorCode,
        String recognitionMode,
        String fileName,
        String fileFormat,
        long byteSize,
        int rowCount,
        int readyCount,
        int needsReviewCount,
        int skippedCount,
        double confidenceThreshold,
        String modelName,
        String modelProvider,
        Instant expiresAt,
        List<Candidate> candidates
) {
    public record Candidate(
            String id,
            int rowIndex,
            double confidence,
            String bucket,
            List<String> issues,
            String duplicateAction,
            JsonNode payload
    ) {
    }
}
