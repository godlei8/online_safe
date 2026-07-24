package com.godlei.onlinesafe.cos;

import com.godlei.onlinesafe.auth.application.ProfileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地开发用：文件落到磁盘，URL 走后端受控下载接口。
 */
public class LocalAvatarStorage implements AvatarObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalAvatarStorage.class);

    private final Path root;

    public LocalAvatarStorage(CosProperties properties) {
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建本地头像目录: " + root, exception);
        }
    }

    @Override
    public String upload(String objectKey, InputStream content, long contentLength, String contentType) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new ProfileException("AVATAR_INVALID", "头像路径不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return "/api/v1/profile/avatar-file/" + objectKey;
        } catch (IOException exception) {
            log.error("本地头像保存失败 key={}", objectKey, exception);
            throw new ProfileException("COS_UPLOAD_FAILED", "头像上传失败，请稍后再试");
        }
    }

    public Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return null;
        }
        return target;
    }
}
