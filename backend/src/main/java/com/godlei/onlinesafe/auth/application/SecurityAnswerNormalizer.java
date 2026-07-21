package com.godlei.onlinesafe.auth.application;

import org.springframework.stereotype.Component;

/**
 * 密保答案规范化：去首尾空白、折叠连续空白、转小写。答案只用于哈希比对，绝不参与 DEK 解包。
 */
@Component
public class SecurityAnswerNormalizer {

    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
