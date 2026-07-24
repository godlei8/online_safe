package com.godlei.onlinesafe.cos;

import com.godlei.onlinesafe.auth.application.ProfileException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class TencentCosAvatarStorage implements AvatarObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(TencentCosAvatarStorage.class);

    private final CosProperties properties;
    private final COSClient client;

    public TencentCosAvatarStorage(CosProperties properties) {
        this.properties = properties;
        if (properties.secretId().isBlank()
                || properties.secretKey().isBlank()
                || properties.region().isBlank()
                || properties.bucket().isBlank()) {
            throw new IllegalStateException("腾讯云 COS 配置不完整");
        }
        COSCredentials credentials = new BasicCOSCredentials(properties.secretId(), properties.secretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.region()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        this.client = new COSClient(credentials, clientConfig);
    }

    @Override
    public String upload(String objectKey, InputStream content, long contentLength, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            metadata.setContentType(contentType);
            PutObjectRequest request = new PutObjectRequest(properties.bucket(), objectKey, content, metadata);
            client.putObject(request);
            return publicUrl(objectKey);
        } catch (ProfileException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("腾讯云 COS 上传失败 key={}", objectKey, exception);
            throw new ProfileException("COS_UPLOAD_FAILED", "头像上传失败，请稍后再试");
        }
    }

    private String publicUrl(String objectKey) {
        if (!properties.publicBaseUrl().isBlank()) {
            return properties.publicBaseUrl() + "/" + objectKey;
        }
        return "https://" + properties.bucket() + ".cos." + properties.region() + ".myqcloud.com/" + objectKey;
    }
}
