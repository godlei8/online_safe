package com.godlei.onlinesafe.vault.web;

import java.time.Instant;

public record VaultCipherEnvelopeResponse(
        String id,
        String ciphertextBase64,
        String nonceBase64,
        int algoVersion,
        int payloadVersion,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
