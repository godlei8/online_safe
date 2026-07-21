package com.godlei.onlinesafe.vault.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VaultCipherEnvelopeRequest(
        @NotBlank String id,
        @NotBlank String ciphertextBase64,
        @NotBlank String nonceBase64,
        @NotNull @Min(1) Integer algoVersion,
        @NotNull @Min(1) Integer payloadVersion,
        @NotNull @Min(0) Long revision
) {
}
