package com.godlei.onlinesafe.vault.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record VaultRecordRequest(
        @NotBlank String id,
        @NotNull JsonNode payload,
        @NotNull @Min(0) Long revision
) {
}
