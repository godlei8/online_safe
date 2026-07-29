package com.godlei.onlinesafe.settings.web;

import java.util.List;

public record SystemSettingItemResponse(
        String key,
        String labelZh,
        String type,
        Object value,
        Object defaultValue,
        long version,
        Integer min,
        Integer max,
        List<String> allowedValues,
        boolean editable,
        String riskLevel,
        boolean secret,
        boolean configured
) {
}
