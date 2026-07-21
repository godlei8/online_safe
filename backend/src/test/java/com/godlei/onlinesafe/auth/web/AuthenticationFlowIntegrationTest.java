package com.godlei.onlinesafe.auth.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.application.InvitationCodeHasher;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.domain.RegistrationInvite;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRepository;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
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

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationFlowIntegrationTest {

    private static final String TEST_ADMIN_PASSWORD = "test-admin-password-123";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

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

    private MockMvc mockMvc;
    private String adminId;

    @BeforeEach
    void setUp() {
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
    void registrationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138000", "alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void registersAndRejectsDuplicateIdentifier() throws Exception {
        seedInvite("TEST_INVITE_CODE", 10);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138000", "alice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.maskedPhone").value("****8000"))
                .andExpect(jsonPath("$.phoneVerified").value(false));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("+8613800138000", "alice-2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_IDENTIFIER_ALREADY_EXISTS"));
    }

    @Test
    void rejectsMismatchedConfirmationPassword() throws Exception {
        seedInvite("TEST_INVITE_CODE", 10);
        String json = objectMapper.writeValueAsString(Map.of(
                "phone", "13800138000",
                "username", "alice",
                "password", "correct-password-123",
                "confirmPassword", "different-password-123",
                "invitationCode", "TEST_INVITE_CODE"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "phone", "13800138000",
                "username", "alice",
                "password", "1234567",
                "confirmPassword", "1234567",
                "invitationCode", "TEST_INVITE_CODE"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsInvalidInvitationCode() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138000", "alice", "WRONG_CODE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_CODE_INVALID"));
    }

    @Test
    void logsInByUsernameAndProtectsRoleBoundaries() throws Exception {
        seedInvite("TEST_INVITE_CODE", 10);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138000", "alice")))
                .andExpect(status().isCreated());

        MockHttpSession session = new MockHttpSession();
        String loginJson = objectMapper.writeValueAsString(Map.of(
                "identifier", "ALICE",
                "password", "correct-password-123"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(get("/api/v1/ping").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/admin/v1/ping").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void logsInByNormalizedPhone() throws Exception {
        seedInvite("TEST_INVITE_CODE", 10);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138000", "alice")))
                .andExpect(status().isCreated());

        MockHttpSession session = new MockHttpSession();
        String loginJson = objectMapper.writeValueAsString(Map.of(
                "identifier", "0086 13800138000",
                "password", "correct-password-123"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void adminLoginAccessesAdminApiAndIsBlockedFromUserApi() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String loginJson = objectMapper.writeValueAsString(Map.of(
                "username", "admin",
                "password", TEST_ADMIN_PASSWORD
        ));

        mockMvc.perform(post("/api/admin/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/api/admin/v1/ping").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ping").session(session))
                .andExpect(status().isForbidden());
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

    private String registrationJson(String phone, String username) throws Exception {
        return registrationJson(phone, username, "TEST_INVITE_CODE");
    }

    private String registrationJson(String phone, String username, String invitationCode) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "phone", phone,
                "username", username,
                "password", "correct-password-123",
                "confirmPassword", "correct-password-123",
                "invitationCode", invitationCode
        ));
    }
}
