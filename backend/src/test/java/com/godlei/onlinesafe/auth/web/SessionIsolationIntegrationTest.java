package com.godlei.onlinesafe.auth.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.infrastructure.SmsVerificationRepository;
import com.godlei.onlinesafe.security.SurfaceAwareCookieHttpSessionIdResolver;
import com.godlei.onlinesafe.sms.RecordingSmsSender;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SessionIsolationIntegrationTest {

    private static final String TEST_ADMIN_PASSWORD = "test-admin-password-123";
    private static final String USER_PASSWORD = "correct-password-123";

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

    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter springSessionRepositoryFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        smsVerificationRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        recordingSmsSender.clear();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(TEST_ADMIN_PASSWORD)));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSessionRepositoryFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void personalAndAdminSessionsCanCoexistInSameBrowser() throws Exception {
        registerUser("13800138001", "alice");

        Cookie userSession = loginUser("alice");
        assertThat(userSession.getName()).isEqualTo(SurfaceAwareCookieHttpSessionIdResolver.USER_COOKIE);

        Cookie adminSession = loginAdmin();
        assertThat(adminSession.getName()).isEqualTo(SurfaceAwareCookieHttpSessionIdResolver.ADMIN_COOKIE);
        assertThat(adminSession.getValue()).isNotEqualTo(userSession.getValue());

        mockMvc.perform(get("/api/auth/session").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"));

        mockMvc.perform(get("/api/v1/ping").cookie(userSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/auth/session").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/api/admin/v1/ping").cookie(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    void adminLogoutDoesNotInvalidatePersonalSession() throws Exception {
        registerUser("13800138002", "bob");
        Cookie userSession = loginUser("bob");
        Cookie adminSession = loginAdmin();

        mockMvc.perform(post("/api/admin/auth/logout").cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/session").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("bob"));

        mockMvc.perform(get("/api/v1/ping").cookie(userSession))
                .andExpect(status().isOk());
    }

    @Test
    void samePersonalAccountAllowsTwoSessionsAndThirdExpiresOldest() throws Exception {
        registerUser("13800138003", "carol");

        Cookie first = loginUser("carol");
        Cookie second = loginUser("carol");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(get("/api/v1/ping").cookie(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ping").cookie(second)).andExpect(status().isOk());

        Cookie third = loginUser("carol");
        mockMvc.perform(get("/api/v1/ping").cookie(third)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ping").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REPLACED"));
        mockMvc.perform(get("/api/v1/ping").cookie(second)).andExpect(status().isOk());
    }

    private void registerUser(String phone, String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awaitRegistrationJson(phone, username)))
                .andExpect(status().isCreated());
    }

    private Cookie loginUser(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", USER_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(SurfaceAwareCookieHttpSessionIdResolver.USER_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", TEST_ADMIN_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(SurfaceAwareCookieHttpSessionIdResolver.ADMIN_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
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
        body.put("password", USER_PASSWORD);
        body.put("confirmPassword", USER_PASSWORD);
        return objectMapper.writeValueAsString(body);
    }
}
