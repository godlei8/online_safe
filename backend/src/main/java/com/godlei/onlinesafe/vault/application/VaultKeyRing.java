package com.godlei.onlinesafe.vault.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 多密钥环：加密使用 currentKeyId；解密按记录 key_id 选择历史密钥。
 * 兼容单钥配置 app.vault.encryption-key；可选 app.vault.keyring-file。
 */
@Component
public class VaultKeyRing {

    private final int currentKeyId;
    private final Map<Integer, SecretKey> keys;

    public VaultKeyRing(
            @Value("${app.vault.encryption-key:}") String encryptionKeyBase64,
            @Value("${app.vault.current-key-id:1}") int currentKeyId,
            @Value("${app.vault.keyring-file:}") String keyringFile,
            ObjectMapper objectMapper
    ) {
        Map<Integer, SecretKey> loaded = new HashMap<>();
        int resolvedCurrent = currentKeyId;

        if (keyringFile != null && !keyringFile.isBlank()) {
            try {
                String json = Files.readString(Path.of(keyringFile.trim()));
                JsonNode root = objectMapper.readTree(json);
                if (root.has("currentKeyId")) {
                    resolvedCurrent = root.get("currentKeyId").asInt();
                }
                JsonNode keysNode = root.get("keys");
                if (keysNode == null || !keysNode.isObject() || keysNode.isEmpty()) {
                    throw new IllegalStateException("Vault 密钥环文件缺少 keys");
                }
                Iterator<String> fieldNames = keysNode.propertyNames().iterator();
                while (fieldNames.hasNext()) {
                    String name = fieldNames.next();
                    int id = Integer.parseInt(name);
                    loaded.put(id, decodeKey(keysNode.get(name).asText(), id));
                }
            } catch (IOException | NumberFormatException exception) {
                throw new IllegalStateException("无法加载 Vault 密钥环文件: " + keyringFile, exception);
            }
        } else if (encryptionKeyBase64 != null && !encryptionKeyBase64.isBlank()) {
            if (resolvedCurrent < 1) {
                throw new IllegalStateException("保险箱 current-key-id 必须 >= 1");
            }
            loaded.put(resolvedCurrent, decodeKey(encryptionKeyBase64, resolvedCurrent));
        } else {
            throw new IllegalStateException("未配置 app.vault.encryption-key 或 app.vault.keyring-file");
        }

        if (resolvedCurrent < 1) {
            throw new IllegalStateException("保险箱 current-key-id 必须 >= 1");
        }
        if (!loaded.containsKey(resolvedCurrent)) {
            throw new IllegalStateException("密钥环缺少当前写密钥 currentKeyId=" + resolvedCurrent);
        }
        this.currentKeyId = resolvedCurrent;
        this.keys = Collections.unmodifiableMap(loaded);
    }

    public int currentKeyId() {
        return currentKeyId;
    }

    public SecretKey requireKey(int keyId) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new InvalidVaultEnvelopeException("UNSUPPORTED_KEY_ID", "不支持的密钥版本");
        }
        return key;
    }

    public boolean hasKey(int keyId) {
        return keys.containsKey(keyId);
    }

    public Set<Integer> keyIds() {
        return keys.keySet();
    }

    private static SecretKey decodeKey(String encryptionKeyBase64, int keyId) {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64.trim());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("保险箱加密密钥 keyId=" + keyId + " 必须是 32 字节的 Base64");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
