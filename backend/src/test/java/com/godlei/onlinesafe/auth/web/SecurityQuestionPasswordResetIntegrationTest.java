package com.godlei.onlinesafe.auth.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.application.InvitationCodeHasher;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.domain.RegistrationInvite;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRepository;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.infrastructure.UserSecurityQuestionRepository;
import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.domain.VaultKeyBundle;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
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
    private VaultKeyBundleRepository vaultKeyBundleRepository;

    @Autowired
    private VaultItemRepository vaultItemRepository;

    @Autowired
    private PrivateTemplateRepository privateTemplateRepository;

    private MockMvc mockMvc;
    private String adminId;

    @BeforeEach
    void setUp() {
        privateTemplateRepository.deleteAll();
        vaultItemRepository.deleteAll();
        vaultKeyBundleRepository.deleteAll();
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
    void registersWithSecurityQuestionsAndResetsPassword() throws Exception {
        seedInvite("TEST_INVITE_CODE", 10);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138010", "alice")))
                .andExpect(status().isCreated());

        assertThat(securityQuestionRepository.count()).isEqualTo(2);

        var alice = userRepository.findByNormalizedUsername("alice").orElseThrow();
        seedVaultData(alice.getId());
        assertThat(vaultKeyBundleRepository.existsByOwnerId(alice.getId())).isTrue();
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

        assertThat(vaultKeyBundleRepository.existsByOwnerId(alice.getId())).isFalse();
        assertThat(vaultItemRepository.count()).isZero();
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

        mockMvc.perform(get("/api/v1/vault/key-bundle").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VAULT_NOT_INITIALIZED"));
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

    private void seedVaultData(String ownerId) {
        byte[] salt = new byte[16];
        byte[] nonce = new byte[24];
        byte[] blob = new byte[32];
        Arrays.fill(salt, (byte) 1);
        Arrays.fill(nonce, (byte) 2);
        Arrays.fill(blob, (byte) 3);
        vaultKeyBundleRepository.save(VaultKeyBundle.create(
                ownerId,
                salt,
                2L,
                67108864L,
                blob,
                nonce,
                blob,
                nonce,
                1
        ));
        vaultItemRepository.save(VaultItem.create(
                UUID.randomUUID().toString(),
                ownerId,
                blob,
                nonce,
                1,
                1
        ));
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
