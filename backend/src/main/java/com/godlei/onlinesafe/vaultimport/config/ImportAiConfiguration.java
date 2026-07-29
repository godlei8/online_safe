package com.godlei.onlinesafe.vaultimport.config;

import com.godlei.onlinesafe.vaultimport.application.ImportAiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ImportAiProperties.class)
public class ImportAiConfiguration {

    @Bean
    @Qualifier("importAiRestClientBuilder")
    RestClient.Builder importAiRestClientBuilder() {
        Duration timeout = Duration.ofSeconds(300);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
