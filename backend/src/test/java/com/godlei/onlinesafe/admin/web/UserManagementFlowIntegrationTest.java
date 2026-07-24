package com.godlei.onlinesafe.admin.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRepository;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.AppUserStatus;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.security.SurfaceAwareCookieHttpSessionIdResolver;
import com.godlei.onlinesafe.session.application.UserSessionService;
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
class UserManagementFlowIntegrationTest {

    private static final String TEST_ADMIN_PASSWORD = "test-admin-password-123";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private RegistrationInviteRepository inviteRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter springSessionRepositoryFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inviteRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(TEST_ADMIN_PASSWORD)));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSessionRepositoryFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void adminCanListDisableEnableAndRevokeSessions() throws Exception {
        AppUser user = userRepository.save(AppUser.register(
                "13800138088",
                "alice",
                "alice",
                passwordEncoder.encode("correct-password-123")
        ));

        Cookie userCookie = loginUser("alice");
        AppUser afterLogin = userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterLogin.getLastLoginAt()).isNotNull();
        assertThat(userSessionService.countActiveByPrincipal("alice")).isEqualTo(1);

        Cookie adminCookie = loginAdmin();

        mockMvc.perform(get("/api/admin/v1/users/stats").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.disabled").value(0))
                .andExpect(jsonPath("$.activeLast7Days").value(1));

        mockMvc.perform(get("/api/admin/v1/users").cookie(adminCookie).param("q", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.content[0].maskedPhone").value("138****8088"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].activeSessionCount").value(1));

        mockMvc.perform(post("/api/admin/v1/users/" + user.getId() + "/revoke-sessions")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessionCount").value(0));

        assertThat(userSessionService.countActiveByPrincipal("alice")).isZero();
        mockMvc.perform(get("/api/v1/ping").cookie(userCookie)).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/v1/users/" + user.getId() + "/disable")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(AppUserStatus.DISABLED);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "alice",
                                "password", "correct-password-123"
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/v1/users/" + user.getId() + "/enable")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private Cookie loginUser(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", "correct-password-123"
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
}
