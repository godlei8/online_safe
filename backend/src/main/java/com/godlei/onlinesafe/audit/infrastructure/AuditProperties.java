package com.godlei.onlinesafe.audit.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(
        String fingerprintKey,
        String trustedProxies,
        int failureAggregateLimitPerMinute,
        int purgeBatchSize
) {
    public AuditProperties {
        fingerprintKey = fingerprintKey == null ? "" : fingerprintKey;
        trustedProxies = trustedProxies == null ? "" : trustedProxies;
        if (failureAggregateLimitPerMinute <= 0) {
            failureAggregateLimitPerMinute = 5;
        }
        if (purgeBatchSize <= 0) {
            purgeBatchSize = 5000;
        }
    }

    public List<String> trustedProxyList() {
        if (trustedProxies.isBlank()) {
            return List.of();
        }
        return Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
