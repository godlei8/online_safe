package com.godlei.onlinesafe.cos;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(CosProperties.class)
public class CosConfig {

    @Bean
    LocalAvatarStorage localAvatarStorage(CosProperties properties) {
        return new LocalAvatarStorage(properties);
    }

    @Bean
    @Primary
    AvatarObjectStorage avatarObjectStorage(CosProperties properties, LocalAvatarStorage localAvatarStorage) {
        if ("tencent".equalsIgnoreCase(properties.provider())) {
            return new TencentCosAvatarStorage(properties);
        }
        return localAvatarStorage;
    }
}
