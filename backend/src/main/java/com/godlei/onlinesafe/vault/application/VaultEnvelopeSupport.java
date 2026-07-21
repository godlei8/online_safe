package com.godlei.onlinesafe.vault.application;

import java.util.Base64;
import java.util.UUID;

final class VaultEnvelopeSupport {

    private static final int NONCE_LENGTH = 24;
    private static final int CURRENT_ALGO_VERSION = 1;

    private VaultEnvelopeSupport() {
    }

    static byte[] decodeBase64(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", fieldName + " 不能为空");
        }
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", fieldName + " 不是合法 Base64");
        }
    }

    static String encodeBase64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    static void requireNonce(byte[] nonce, String fieldName) {
        if (nonce.length != NONCE_LENGTH) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", fieldName + " 长度必须为 24 字节");
        }
    }

    static void requireNonEmpty(byte[] data, String fieldName) {
        if (data.length == 0) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", fieldName + " 不能为空");
        }
    }

    static void requireAlgoVersion(int algoVersion) {
        if (algoVersion != CURRENT_ALGO_VERSION) {
            throw new InvalidVaultEnvelopeException("UNSUPPORTED_ALGO_VERSION", "不支持的算法版本");
        }
    }

    static void requirePayloadVersion(int payloadVersion) {
        if (payloadVersion < 1) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "payloadVersion 无效");
        }
    }

    static void requireUuid(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "id 不能为空");
        }
        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "id 必须是 UUID");
        }
    }
}
