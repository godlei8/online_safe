package com.godlei.onlinesafe.vault.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VaultKeyBundleReplaceRequest(
        @NotBlank String kdfSaltBase64,
        @NotNull @Min(1) Long kdfOpsLimit,
        @NotNull @Min(1) Long kdfMemLimit,
        @NotBlank String wrappedDekMasterBase64,
        @NotBlank String wrappedDekMasterNonceBase64,
        @NotBlank String wrappedDekRecoveryBase64,
        @NotBlank String wrappedDekRecoveryNonceBase64,
        @NotNull @Min(1) Integer algoVersion,
        @NotNull @Min(0) Long revision
) {
}
