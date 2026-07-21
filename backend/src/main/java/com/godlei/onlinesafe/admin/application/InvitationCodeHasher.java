package com.godlei.onlinesafe.admin.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class InvitationCodeHasher {

    public String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    }

    public String hash(String rawCode) {
        String normalized = normalize(rawCode);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public String hint(String rawCode) {
        String normalized = normalize(rawCode);
        if (normalized.length() < 8) {
            return "****";
        }
        return normalized.substring(0, 3) + "-****-" + normalized.substring(normalized.length() - 4);
    }
}
