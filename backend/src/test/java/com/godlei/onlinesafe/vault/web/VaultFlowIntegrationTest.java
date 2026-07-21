package com.godlei.onlinesafe.vault.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultKeyBundleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class VaultFlowIntegrationTest {

    private static final String PASSWORD = "correct-password-123";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VaultKeyBundleRepository keyBundleRepository;

    @Autowired
    private VaultItemRepository itemRepository;

    @Autowired
    private PrivateTemplateRepository templateRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        templateRepository.deleteAll();
        itemRepository.deleteAll();
        keyBundleRepository.deleteAll();
        userRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void keyBundleItemAndTemplateFlowWithOwnerIsolation() throws Exception {
        AppUser alice = saveUser("13800138001", "alice");
        AppUser bob = saveUser("13800138002", "bob");
        MockHttpSession aliceSession = login("alice");
        MockHttpSession bobSession = login("bob");

        mockMvc.perform(get("/api/v1/vault/key-bundle").session(aliceSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VAULT_NOT_INITIALIZED"));

        Map<String, Object> bundle = sampleKeyBundle();
        mockMvc.perform(put("/api/v1/vault/key-bundle")
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.algoVersion").value(1))
                .andExpect(jsonPath("$.revision").value(0));

        mockMvc.perform(put("/api/v1/vault/key-bundle")
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAULT_ALREADY_INITIALIZED"));

        mockMvc.perform(get("/api/v1/vault/key-bundle").session(bobSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/vault/key-bundle").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kdfOpsLimit").value(2));

        String itemId = UUID.randomUUID().toString();
        Map<String, Object> itemCreate = sampleEnvelope(itemId, 0L);
        mockMvc.perform(post("/api/v1/vault/items")
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemCreate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.revision").value(0));

        mockMvc.perform(get("/api/v1/vault/items/" + itemId).session(bobSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/vault/items").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        Map<String, Object> itemUpdate = new HashMap<>(sampleEnvelope(itemId, 0L));
        itemUpdate.put("ciphertextBase64", Base64.getEncoder().encodeToString("updated-cipher".getBytes()));
        MvcResult updated = mockMvc.perform(put("/api/v1/vault/items/" + itemId)
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andReturn();

        assertThat(updated.getResponse().getContentAsString()).doesNotContain("password");
        assertThat(updated.getResponse().getContentAsString()).doesNotContain(alice.getUsername());

        mockMvc.perform(put("/api/v1/vault/items/" + itemId)
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEnvelope(itemId, 0L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAULT_REVISION_CONFLICT"));

        String templateId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/vault/private-templates")
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEnvelope(templateId, 0L))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/vault/private-templates/" + templateId).session(bobSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/vault/items/" + itemId)
                        .session(aliceSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/vault/items/" + itemId).session(aliceSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/vault/private-templates/" + templateId)
                        .session(aliceSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(keyBundleRepository.findByOwnerId(alice.getId())).isPresent();
        assertThat(keyBundleRepository.findByOwnerId(bob.getId())).isEmpty();
    }

    private AppUser saveUser(String phone, String username) {
        return userRepository.save(AppUser.register(
                phone,
                username,
                username,
                passwordEncoder.encode(PASSWORD)
        ));
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static Map<String, Object> sampleKeyBundle() {
        return Map.of(
                "kdfSaltBase64", b64(16),
                "kdfOpsLimit", 2L,
                "kdfMemLimit", 8192L,
                "wrappedDekMasterBase64", b64(48),
                "wrappedDekMasterNonceBase64", b64(24),
                "wrappedDekRecoveryBase64", b64(48),
                "wrappedDekRecoveryNonceBase64", b64(24),
                "algoVersion", 1
        );
    }

    private static Map<String, Object> sampleEnvelope(String id, long revision) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("id", id);
        envelope.put("ciphertextBase64", Base64.getEncoder().encodeToString("cipher-bytes".getBytes()));
        envelope.put("nonceBase64", b64(24));
        envelope.put("algoVersion", 1);
        envelope.put("payloadVersion", 1);
        envelope.put("revision", revision);
        return envelope;
    }

    private static String b64(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
