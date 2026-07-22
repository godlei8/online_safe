package com.godlei.onlinesafe.auth.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.infrastructure.SmsVerificationRepository;
import com.godlei.onlinesafe.sms.RecordingSmsSender;
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
    private SmsVerificationRepository smsVerificationRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordingSmsSender recordingSmsSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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
    void registrationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("13800138000", "alice", "123456")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void registersAndRejectsDuplicateIdentifier() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awaitRegistrationJson("13800138000", "alice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.maskedPhone").value("****8000"))
                .andExpect(jsonPath("$.phoneVerified").value(true));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("+8613800138000", "alice-2", "000000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_IDENTIFIER_ALREADY_EXISTS"));
    }

    @Test
    void rejectsMismatchedConfirmationPassword() throws Exception {
        String code = sendRegisterCode("13800138000");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", "13800138000");
        body.put("smsCode", code);
        body.put("username", "alice");
        body.put("password", "correct-password-123");
        body.put("confirmPassword", "different-password-123");
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", "13800138000");
        body.put("smsCode", "123456");
        body.put("username", "alice");
        body.put("password", "1234567");
        body.put("confirmPassword", "1234567");
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void logsInByUsernameAndProtectsRoleBoundaries() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awaitRegistrationJson("13800138000", "alice")))
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
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awaitRegistrationJson("13800138000", "alice")))
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

    private String awaitRegistrationJson(String phone, String username) throws Exception {
        return registrationJson(phone, username, sendRegisterCode(phone));
    }

    private String sendRegisterCode(String phone) throws Exception {
        mockMvc.perform(post("/api/auth/sms/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "purpose", "REGISTER"
                        ))))
                .andExpect(status().isNoContent());
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() > 11) {
            digits = digits.substring(2);
        }
        return recordingSmsSender.requireLatestCode("+86" + digits, SmsPurpose.REGISTER);
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
