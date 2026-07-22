package com.godlei.onlinesafe.auth.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.application.InvitationCodeHasher;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.domain.RegistrationInvite;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRepository;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.infrastructure.UserSecurityQuestionRepository;
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
class SecurityQuestionPasswordResetIntegrationTest {

    private static final String TEST_ADMIN_PASSWORD = "test-admin-password-123";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserSecurityQuestionRepository securityQuestionRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private RegistrationInviteRepository inviteRepository;

    @Autowired
    private InvitationCodeHasher invitationCodeHasher;

    @Autowired
    private com.godlei.onlinesafe.admin.application.InvitationCodeCipher invitationCodeCipher;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VaultItemRepository vaultItemRepository;

    @Autowired
    private PrivateTemplateRepository privateTemplateRepository;

    @Autowired
    private VaultPayloadCipher vaultPayloadCipher;

    private MockMvc mockMvc;
    private String adminId;

    @BeforeEach
    void setUp() {
        privateTemplateRepository.deleteAll();
        vaultItemRepository.deleteAll();
        securityQuestionRepository.deleteAll();
        inviteRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        AdminUser admin = adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(TEST_ADMIN_PASSWORD)));
        adminId = admin.getId();
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void registersWithSecurityQuestionsAndResetsPasswordWithoutClearingVault() throws Exception {
        seedInvite("TEST_INVITE_CODE", 10);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138010", "alice")))
                .andExpect(status().isCreated());

        assertThat(securityQuestionRepository.count()).isEqualTo(2);

        var alice = userRepository.findByNormalizedUsername("alice").orElseThrow();
        String itemId = seedVaultItem(alice.getId());
        assertThat(vaultItemRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/auth/security-questions/builtins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].text").exists());

        mockMvc.perform(post("/api/auth/password-reset/lookup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", "alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].questionText").isNotEmpty());

        mockMvc.perform(post("/api/auth/password-reset/lookup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", "nobody-here"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "alice",
                                "answers", List.of("wrong", "wrong"),
                                "newPassword", "new-password-456",
                                "confirmPassword", "new-password-456"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "alice",
                                "answers", List.of("Fluffy", "Shanghai"),
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
    void rejectsRegistrationWithoutSecurityQuestions() throws Exception {
        seedInvite("TEST_INVITE_CODE", 10);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", "13800138011");
        body.put("username", "bob");
        body.put("password", "correct-password-123");
        body.put("confirmPassword", "correct-password-123");
        body.put("invitationCode", "TEST_INVITE_CODE");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void seedInvite(String plainCode, int maxUses) {
        inviteRepository.save(RegistrationInvite.create(
                invitationCodeHasher.hash(plainCode),
                invitationCodeHasher.hint(plainCode),
                invitationCodeCipher.encrypt(plainCode),
                maxUses,
                null,
                adminId,
                "测试邀请码"
        ));
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

    private String registrationJson(String phone, String username) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("username", username);
        body.put("password", "correct-password-123");
        body.put("confirmPassword", "correct-password-123");
        body.put("invitationCode", "TEST_INVITE_CODE");
        body.put("securityQuestions", List.of(
                Map.of(
                        "questionType", "BUILTIN",
                        "questionCode", "PET_NAME",
                        "answer", "Fluffy"
                ),
                Map.of(
                        "questionType", "CUSTOM",
                        "questionText", "你最喜欢的城市是哪里？",
                        "answer", "Shanghai"
                )
        ));
        return objectMapper.writeValueAsString(body);
    }
}
