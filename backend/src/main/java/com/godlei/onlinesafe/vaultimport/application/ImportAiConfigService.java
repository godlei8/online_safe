package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.settings.application.SystemSettingService;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 合并管理端配置与环境变量默认值；管理端非空配置优先。
 */
@Service
public class ImportAiConfigService {

    private final ImportAiProperties envProperties;
    private final SystemSettingService systemSettingService;

    public ImportAiConfigService(ImportAiProperties envProperties, SystemSettingService systemSettingService) {
        this.envProperties = envProperties;
        this.systemSettingService = systemSettingService;
    }

    public boolean isEnabled() {
        if (hasAdminModelConfig()) {
            return systemSettingService.aiModelEnabled();
        }
        return envProperties.enabled();
    }

    public String baseUrl() {
        String admin = systemSettingService.aiModelBaseUrl();
        if (!admin.isBlank()) {
            return admin;
        }
        return envProperties.baseUrl() == null ? "" : envProperties.baseUrl().trim();
    }

    public String apiKey() {
        String admin = systemSettingService.aiModelApiKeyRaw();
        if (!admin.isBlank()) {
            return admin;
        }
        return envProperties.apiKey() == null ? "" : envProperties.apiKey().trim();
    }

    public String model() {
        String admin = systemSettingService.aiModelName();
        if (!admin.isBlank()) {
            return admin;
        }
        return envProperties.model() == null ? "" : envProperties.model().trim();
    }

    public Duration timeout() {
        if (hasAdminModelConfig()) {
            return Duration.ofSeconds(systemSettingService.aiModelTimeoutSeconds());
        }
        return envProperties.timeout() == null ? Duration.ofSeconds(60) : envProperties.timeout();
    }

    public int maxRetries() {
        if (hasAdminModelConfig()) {
            return systemSettingService.aiModelMaxRetries();
        }
        return Math.max(0, envProperties.maxRetries());
    }

    public boolean isConfigured() {
        return isEnabled()
                && !baseUrl().isBlank()
                && !apiKey().isBlank()
                && !model().isBlank();
    }

    public String providerHint() {
        return "openai-compatible";
    }

    private boolean hasAdminModelConfig() {
        return systemSettingService.aiModelEnabled()
                || !systemSettingService.aiModelBaseUrl().isBlank()
                || !systemSettingService.aiModelName().isBlank()
                || !systemSettingService.aiModelApiKeyRaw().isBlank();
    }
}
