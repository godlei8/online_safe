package com.godlei.onlinesafe.settings.web;

import java.util.List;
import java.util.Map;

public record SystemSettingsResponse(
        Map<String, List<SystemSettingItemResponse>> groups,
        Map<String, Object> capabilities
) {
}
