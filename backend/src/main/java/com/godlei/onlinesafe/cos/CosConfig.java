package com.godlei.onlinesafe.cos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(CosProperties.class)
public class CosConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.cos", name = "provider", havingValue = "local", matchIfMissing = true)
    LocalAvatarStorage localAvatarStorage(CosProperties properties) {
        return new LocalAvatarStorage(properties);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.cos", name = "provider", havingValue = "local", matchIfMissing = true)
    AvatarObjectStorage localAvatarObjectStorage(LocalAvatarStorage localAvatarStorage) {
        return localAvatarStorage;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.cos", name = "provider", havingValue = "tencent")
    AvatarObjectStorage tencentAvatarObjectStorage(CosProperties properties) {
        return new TencentCosAvatarStorage(properties);
    }
}
