package com.godlei.onlinesafe.session.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.infrastructure.SmsVerificationRepository;
import com.godlei.onlinesafe.security.SurfaceAwareCookieHttpSessionIdResolver;
import com.godlei.onlinesafe.settings.application.SystemSettingRegistry;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import com.godlei.onlinesafe.settings.domain.SettingValueType;
import com.godlei.onlinesafe.settings.domain.SystemSetting;
import com.godlei.onlinesafe.settings.infrastructure.SystemSettingRepository;
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

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class UserSessionManagementIntegrationTest {

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
    private SystemSettingRepository systemSettingRepository;
    @Autowired
    private SystemSettingService systemSettingService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RecordingSmsSender recordingSmsSender;
    @Autowired
    private Clock clock;
    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter springSessionRepositoryFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        smsVerificationRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        systemSettingRepository.deleteAll();
        systemSettingService.invalidateCache();
        recordingSmsSender.clear();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(TEST_ADMIN_PASSWORD)));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSessionRepositoryFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void userCanKeepTwoSessionsAndThirdReplacesOldest() throws Exception {
        registerUser("13800138101", "carol");
        Cookie first = loginUser("carol");
        Thread.sleep(20);
        Cookie second = loginUser("carol");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(get("/api/v1/ping").cookie(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ping").cookie(second)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/security/sessions").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(2))
                .andExpect(jsonPath("$.maxActiveSessions").value(2));

        Thread.sleep(20);
        Cookie third = loginUser("carol");
        mockMvc.perform(get("/api/v1/ping").cookie(third)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ping").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REPLACED"));
    }

    @Test
    void adminStillLimitedToOneSession() throws Exception {
        Cookie first = loginAdmin();
        Cookie second = loginAdmin();
        mockMvc.perform(get("/api/admin/v1/ping").cookie(second)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/v1/ping").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REPLACED"));
    }

    @Test
    void userCanListAndRevokeOtherSessions() throws Exception {
        registerUser("13800138102", "dave");
        Cookie first = loginUser("dave");
        Thread.sleep(20);
        Cookie second = loginUser("dave");

        MvcResult list = mockMvc.perform(get("/api/v1/security/sessions").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(2))
                .andReturn();
        String body = list.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SPRING_SESSION");
        assertThat(body).doesNotContain(first.getValue());

        String revokeId = null;
        for (var node : objectMapper.readTree(body).get("sessions")) {
            if (!node.get("current").asBoolean()) {
                revokeId = node.get("id").asText();
                break;
            }
        }
        assertThat(revokeId).isNotBlank();

        mockMvc.perform(delete("/api/v1/security/sessions/" + revokeId).cookie(second).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/ping").cookie(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/security/sessions").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(1));
    }

    @Test
    void revokeOthersKeepsCurrent() throws Exception {
        registerUser("13800138103", "erin");
        Cookie first = loginUser("erin");
        Thread.sleep(20);
        Cookie second = loginUser("erin");

        mockMvc.perform(post("/api/v1/security/sessions/revoke-others").cookie(second).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedCount").value(1));
        mockMvc.perform(get("/api/v1/ping").cookie(second)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ping").cookie(first)).andExpect(status().isUnauthorized());
    }

    @Test
    void revokeAllInvalidatesCurrent() throws Exception {
        registerUser("13800138104", "frank");
        Cookie session = loginUser("frank");
        mockMvc.perform(post("/api/v1/security/sessions/revoke-all").cookie(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/ping").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void cannotRevokeCurrentViaDelete() throws Exception {
        registerUser("13800138105", "gina");
        Cookie session = loginUser("gina");
        MvcResult list = mockMvc.perform(get("/api/v1/security/sessions").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        String currentId = null;
        for (var node : objectMapper.readTree(list.getResponse().getContentAsString()).get("sessions")) {
            if (node.get("current").asBoolean()) {
                currentId = node.get("id").asText();
            }
        }
        mockMvc.perform(delete("/api/v1/security/sessions/" + currentId).cookie(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CURRENT_SESSION_USE_LOGOUT"));
    }

    @Test
    void unknownPublicIdIsIdempotentNoContent() throws Exception {
        registerUser("13800138106", "hank");
        Cookie session = loginUser("hank");
        mockMvc.perform(delete("/api/v1/security/sessions/00000000-0000-0000-0000-000000000000")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void settingRaisesMaxSessions() throws Exception {
        systemSettingRepository.save(SystemSetting.create(
                SystemSettingRegistry.MAX_ACTIVE_USER_SESSIONS,
                3,
                SettingValueType.INTEGER,
                "admin",
                clock.instant()
        ));
        systemSettingService.invalidateCache();
        registerUser("13800138107", "ivy");
        Cookie a = loginUser("ivy");
        Thread.sleep(15);
        Cookie b = loginUser("ivy");
        Thread.sleep(15);
        Cookie c = loginUser("ivy");
        mockMvc.perform(get("/api/v1/ping").cookie(a)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ping").cookie(b)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ping").cookie(c)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/security/sessions").cookie(c))
                .andExpect(jsonPath("$.activeCount").value(3))
                .andExpect(jsonPath("$.maxActiveSessions").value(3));
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
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(SurfaceAwareCookieHttpSessionIdResolver.USER_COOKIE);
        assertThat(cookie).isNotNull();
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
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(SurfaceAwareCookieHttpSessionIdResolver.ADMIN_COOKIE);
        assertThat(cookie).isNotNull();
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
