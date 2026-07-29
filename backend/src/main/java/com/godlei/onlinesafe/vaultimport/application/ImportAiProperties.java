package com.godlei.onlinesafe.vaultimport.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.import.ai")
public record ImportAiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        Duration timeout,
        int maxRetries
) {
    public ImportAiProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(60);
        }
        if (maxRetries < 0) {
            maxRetries = 0;
        }
    }

    public boolean isConfigured() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
