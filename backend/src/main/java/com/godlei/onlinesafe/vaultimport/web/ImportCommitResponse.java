package com.godlei.onlinesafe.vaultimport.web;

import java.util.List;

public record ImportCommitResponse(
        String sessionId,
        int succeededCount,
        int failedCount,
        List<Failure> failures
) {
    public record Failure(String candidateId, int rowIndex, String errorCode) {
    }
}
