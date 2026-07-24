package com.godlei.onlinesafe.cos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cos")
public record CosProperties(
        String provider,
        String secretId,
        String secretKey,
        String region,
        String bucket,
        /** 业务根前缀，如 online-safe；可为空 */
        String prefix,
        String pathPrefix,
        String publicBaseUrl,
        String localDir
) {
    public CosProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local";
        }
        secretId = secretId == null ? "" : secretId;
        secretKey = secretKey == null ? "" : secretKey;
        region = region == null ? "" : region;
        bucket = bucket == null ? "" : bucket;
        prefix = prefix == null ? "" : prefix.replaceAll("^/+|/+$", "");
        pathPrefix = pathPrefix == null || pathPrefix.isBlank() ? "avatars" : pathPrefix.replaceAll("^/+|/+$", "");
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        localDir = localDir == null || localDir.isBlank() ? "./data/avatars" : localDir;
    }

    /** 对象键目录前缀：{prefix}/{pathPrefix} 或仅 pathPrefix */
    public String objectDirPrefix() {
        if (prefix.isBlank()) {
            return pathPrefix;
        }
        return prefix + "/" + pathPrefix;
    }
}
