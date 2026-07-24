package com.godlei.onlinesafe.settings.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemSettingChangeRequest(
        @NotBlank String key,
        @NotNull Object value,
        Long expectedVersion
) {
}
