package com.godlei.onlinesafe.settings.domain;

import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;

import java.util.List;
import java.util.Set;

public record SystemSettingDefinition(
        String key,
        String group,
        String labelZh,
        SettingValueType type,
        Object defaultValue,
        Set<Object> allowedValues,
        Integer min,
        Integer max,
        boolean editable,
        String effectMode,
        AuditRiskLevel riskLevel
) {
    public static SystemSettingDefinition enumSetting(
            String key,
            String group,
            String labelZh,
            Object defaultValue,
            List<?> allowed,
            AuditRiskLevel riskLevel
    ) {
        return new SystemSettingDefinition(
                key, group, labelZh, SettingValueType.ENUM, defaultValue,
                Set.copyOf(allowed), null, null, true, "IMMEDIATE", riskLevel
        );
    }

    public static SystemSettingDefinition intSetting(
            String key,
            String group,
            String labelZh,
            int defaultValue,
            int min,
            int max,
            AuditRiskLevel riskLevel
    ) {
        return new SystemSettingDefinition(
                key, group, labelZh, SettingValueType.INTEGER, defaultValue,
                Set.of(), min, max, true, "IMMEDIATE", riskLevel
        );
    }
}
