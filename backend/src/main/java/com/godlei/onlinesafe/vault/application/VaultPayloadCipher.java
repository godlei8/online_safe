package com.godlei.onlinesafe.vault.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Component
public class VaultPayloadCipher {

    public static final int ALGO_VERSION = 2;
    public static final int PAYLOAD_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final VaultKeyRing keyRing;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public VaultPayloadCipher(VaultKeyRing keyRing, ObjectMapper objectMapper) {
        this.keyRing = keyRing;
        this.objectMapper = objectMapper;
    }

    public int currentKeyId() {
        return keyRing.currentKeyId();
    }

    public SealedPayload encrypt(
            String ownerId,
            String entityType,
            String entityId,
            JsonNode payload
    ) {
        int keyId = keyRing.currentKeyId();
        SecretKey secretKey = keyRing.requireKey(keyId);
        try {
            byte[] plain = objectMapper.writeValueAsBytes(payload);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(ownerId, entityType, entityId, PAYLOAD_VERSION));
            byte[] ciphertext = cipher.doFinal(plain);
            return new SealedPayload(ciphertext, iv, ALGO_VERSION, PAYLOAD_VERSION, keyId);
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalStateException("保险箱载荷加密失败", exception);
        }
    }

    public JsonNode decrypt(
            String ownerId,
            String entityType,
            String entityId,
            byte[] ciphertext,
            byte[] nonce,
            int algoVersion,
            int payloadVersion,
            int keyId
    ) {
        if (algoVersion != ALGO_VERSION) {
            throw new InvalidVaultEnvelopeException("UNSUPPORTED_ALGO_VERSION", "不支持的算法版本");
        }
        if (nonce == null || nonce.length != IV_LENGTH) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "密文 nonce 无效");
        }
        SecretKey secretKey = keyRing.requireKey(keyId);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(ownerId, entityType, entityId, payloadVersion));
            byte[] plain = cipher.doFinal(ciphertext);
            return objectMapper.readTree(plain);
        } catch (InvalidVaultEnvelopeException exception) {
            throw exception;
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new InvalidVaultEnvelopeException("VAULT_DECRYPT_FAILED", "保险箱数据解密失败");
        }
    }

    private static byte[] aad(String ownerId, String entityType, String entityId, int payloadVersion) {
        String value = ownerId + "|" + entityType + "|" + entityId + "|" + payloadVersion;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public record SealedPayload(
            byte[] ciphertext,
            byte[] nonce,
            int algoVersion,
            int payloadVersion,
            int keyId
    ) {
    }
}
