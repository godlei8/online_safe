package com.godlei.onlinesafe.vault.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultKeyRingTest {

    private static final String KEY1 = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final String KEY2 = Base64.getEncoder().encodeToString(
            "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII));

    @Test
    void singleKeyFallbackWorks() {
        VaultKeyRing ring = new VaultKeyRing(KEY1, 1, "", JsonMapper.builder().build());
        assertEquals(1, ring.currentKeyId());
        assertTrue(ring.hasKey(1));
        assertThrows(InvalidVaultEnvelopeException.class, () -> ring.requireKey(2));
    }

    @Test
    void keyringFileSupportsHistoricalKeys() throws Exception {
        Path file = Files.createTempFile("vault-keyring-", ".json");
        Files.writeString(file, """
                {
                  "currentKeyId": 2,
                  "keys": {
                    "1": "%s",
                    "2": "%s"
                  }
                }
                """.formatted(KEY1, KEY2));
        try {
            VaultKeyRing ring = new VaultKeyRing("", 1, file.toString(), JsonMapper.builder().build());
            assertEquals(2, ring.currentKeyId());
            assertTrue(ring.hasKey(1));
            assertTrue(ring.hasKey(2));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
