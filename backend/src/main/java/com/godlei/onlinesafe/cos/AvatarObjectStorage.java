package com.godlei.onlinesafe.cos;

import java.io.InputStream;

public interface AvatarObjectStorage {

    /**
     * @return 可公网访问的头像 URL
     */
    String upload(String objectKey, InputStream content, long contentLength, String contentType);
}
