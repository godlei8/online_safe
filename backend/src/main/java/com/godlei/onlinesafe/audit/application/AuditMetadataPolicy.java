package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.domain.AuditEventType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class AuditMetadataPolicy {

    public Map<String, Object> sanitize(AuditEventType type, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Set<String> allowed = type.allowedMetadataKeys();
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("审计元数据键不被允许: " + key);
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof String text) {
                String trimmed = text.trim();
                if (trimmed.length() > 256) {
                    trimmed = trimmed.substring(0, 256);
                }
                cleaned.put(key, trimmed);
            } else if (value instanceof Number || value instanceof Boolean) {
                cleaned.put(key, value);
            } else {
                throw new IllegalArgumentException("审计元数据值类型不被允许: " + key);
            }
        }
        return cleaned.isEmpty() ? null : cleaned;
    }
}
