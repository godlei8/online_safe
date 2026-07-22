package com.godlei.onlinesafe.auth.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.infrastructure.SmsVerificationRepository;
import com.godlei.onlinesafe.sms.RecordingSmsSender;
import com.godlei.onlinesafe.vault.application.VaultPayloadCipher;
import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SmsPasswordResetIntegrationTest {

    private static final String TEST_ADMIN_PASSWORD = "test-admin-password-123";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SmsVerificationRepository smsVerificationRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VaultItemRepository vaultItemRepository;

    @Autowired
    private PrivateTemplateRepository privateTemplateRepository;

    @Autowired
    private VaultPayloadCipher vaultPayloadCipher;

    @Autowired
    private RecordingSmsSender recordingSmsSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        privateTemplateRepository.deleteAll();
        vaultItemRepository.deleteAll();
        smsVerificationRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        recordingSmsSender.clear();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(TEST_ADMIN_PASSWORD)));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void registersWithSmsAndResetsPasswordWithoutClearingVault() throws Exception {
        String phone = "13800138010";
        String phoneE164 = "+8613800138010";

        mockMvc.perform(post("/api/auth/sms/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "purpose", "REGISTER"
                        ))))
                .andExpect(status().isNoContent());

        String registerCode = recordingSmsSender.requireLatestCode(phoneE164, SmsPurpose.REGISTER);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(phone, "alice", registerCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneVerified").value(true));

        assertThat(smsVerificationRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByNormalizedUsername("alice").orElseThrow().isPhoneVerified()).isTrue();

        var alice = userRepository.findByNormalizedUsername("alice").orElseThrow();
        String itemId = seedVaultItem(alice.getId());
        assertThat(vaultItemRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/auth/sms/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "purpose", "RESET_PASSWORD"
                        ))))
                .andExpect(status().isNoContent());

        String resetCode = recordingSmsSender.requireLatestCode(phoneE164, SmsPurpose.RESET_PASSWORD);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "smsCode", "000000",
                                "newPassword", "new-password-456",
                                "confirmPassword", "new-password-456"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "smsCode", resetCode,
                                "newPassword", "new-password-456",
                                "confirmPassword", "new-password-456"
                        ))))
                .andExpect(status().isNoContent());

        assertThat(vaultItemRepository.count()).isEqualTo(1);
        assertThat(privateTemplateRepository.count()).isZero();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "alice",
                                "password", "correct-password-123"
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "alice",
                                "password", "new-password-456"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        mockMvc.perform(get("/api/v1/vault/items/" + itemId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.name").value("保留记录"));
    }

    @Test
    void rejectsRegistrationWithoutSmsCode() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", "13800138011");
        body.put("username", "bob");
        body.put("password", "correct-password-123");
        body.put("confirmPassword", "correct-password-123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsSmsSendForUnknownPhoneOnReset() throws Exception {
        mockMvc.perform(post("/api/auth/sms/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", "13800138999",
                                "purpose", "RESET_PASSWORD"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_REGISTERED"));
    }

    private String seedVaultItem(String ownerId) {
        String itemId = UUID.randomUUID().toString();
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "name", "保留记录",
                "platform", "Test",
                "channel", "",
                "status", "NORMAL",
                "fields", List.of(),
                "notes", ""
        ));
        VaultPayloadCipher.SealedPayload sealed = vaultPayloadCipher.encrypt(
                ownerId,
                "ITEM",
                itemId,
                payload
        );
        vaultItemRepository.save(VaultItem.create(
                itemId,
                ownerId,
                sealed.ciphertext(),
                sealed.nonce(),
                sealed.algoVersion(),
                sealed.payloadVersion(),
                sealed.keyId()
        ));
        return itemId;
    }

    private String registrationJson(String phone, String username, String smsCode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("smsCode", smsCode);
        body.put("username", username);
        body.put("password", "correct-password-123");
        body.put("confirmPassword", "correct-password-123");
        return objectMapper.writeValueAsString(body);
    }
}
