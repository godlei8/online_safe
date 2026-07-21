package com.godlei.onlinesafe.vault.web;

import java.time.Instant;

public record VaultKeyBundleResponse(
        String kdfSaltBase64,
        long kdfOpsLimit,
        long kdfMemLimit,
        String wrappedDekMasterBase64,
        String wrappedDekMasterNonceBase64,
        String wrappedDekRecoveryBase64,
        String wrappedDekRecoveryNonceBase64,
        int algoVersion,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
