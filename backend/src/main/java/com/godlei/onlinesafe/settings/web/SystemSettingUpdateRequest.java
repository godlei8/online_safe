package com.godlei.onlinesafe.settings.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SystemSettingUpdateRequest(
        @NotEmpty @Valid List<SystemSettingChangeRequest> changes
) {
}
