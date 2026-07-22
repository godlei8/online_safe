package com.godlei.onlinesafe.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    @Bean
    @Profile("test")
    RecordingSmsSender recordingSmsSender() {
        return new RecordingSmsSender();
    }

    @Bean
    @Profile("!test")
    @ConditionalOnMissingBean(SmsSender.class)
    SmsSender smsSender(SmsProperties properties) {
        String provider = properties.provider().trim().toLowerCase();
        if ("logging".equals(provider) || properties.accessKeyId().isBlank()) {
            return new LoggingSmsSender();
        }
        if (!"aliyun".equals(provider)) {
            throw new IllegalStateException("不支持的短信 provider: " + properties.provider());
        }
        if (properties.accessKeySecret().isBlank()) {
            throw new IllegalStateException("已选择阿里云短信，但未配置 ALIYUN_ACCESS_KEY_SECRET");
        }
        return new AliyunSmsSender(properties);
    }
}
