package com.godlei.onlinesafe.audit.infrastructure;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class AuditFingerprintService {

    private final byte[] keyBytes;

    public AuditFingerprintService(AuditProperties properties) {
        String key = properties.fingerprintKey();
        if (key.isBlank()) {
            // 本地兜底；生产必须注入 AUDIT_FINGERPRINT_KEY
            key = "online-safe-audit-dev-fingerprint-key";
        }
        this.keyBytes = key.getBytes(StandardCharsets.UTF_8);
    }

    public String hmacSha256Hex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("审计指纹计算失败", exception);
        }
    }

    public String sha256Hex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("摘要计算失败", exception);
        }
    }
}
