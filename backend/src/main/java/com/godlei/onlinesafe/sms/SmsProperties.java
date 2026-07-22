package com.godlei.onlinesafe.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        String accessKeyId,
        String accessKeySecret,
        String signName,
        String templateRegister,
        String templateReset,
        int codeTtlSeconds,
        int codeLength,
        int sendIntervalSeconds,
        int sendDailyLimit,
        String provider
) {
    public SmsProperties {
        if (codeTtlSeconds <= 0) {
            codeTtlSeconds = 300;
        }
        if (codeLength < 4 || codeLength > 8) {
            codeLength = 6;
        }
        if (sendIntervalSeconds <= 0) {
            sendIntervalSeconds = 60;
        }
        if (sendDailyLimit <= 0) {
            sendDailyLimit = 10;
        }
        if (provider == null || provider.isBlank()) {
            provider = "aliyun";
        }
        accessKeyId = accessKeyId == null ? "" : accessKeyId;
        accessKeySecret = accessKeySecret == null ? "" : accessKeySecret;
        signName = signName == null ? "" : signName;
        templateRegister = templateRegister == null ? "" : templateRegister;
        templateReset = templateReset == null ? "" : templateReset;
    }
}
