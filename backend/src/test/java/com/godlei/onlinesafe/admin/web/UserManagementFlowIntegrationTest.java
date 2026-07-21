package com.godlei.onlinesafe.admin.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRepository;
import com.godlei.onlinesafe.admin.infrastructure.UserSessionRepository;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.AppUserStatus;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserSessionRepository userSessionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inviteRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(TEST_ADMIN_PASSWORD)));
        ensureSessionTable();
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
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

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "alice",
                                "password", "correct-password-123"
                        ))))
                .andExpect(status().isOk());

        AppUser afterLogin = userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterLogin.getLastLoginAt()).isNotNull();

        insertSession("alice");
        assertThat(userSessionRepository.countByPrincipalName("alice")).isEqualTo(1);

        MockHttpSession adminSession = adminLogin();

        mockMvc.perform(get("/api/admin/v1/users/stats").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.disabled").value(0))
                .andExpect(jsonPath("$.activeLast7Days").value(1));

        mockMvc.perform(get("/api/admin/v1/users").session(adminSession).param("q", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.content[0].maskedPhone").value("138****8088"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].activeSessionCount").value(1));

        mockMvc.perform(post("/api/admin/v1/users/" + user.getId() + "/revoke-sessions")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessionCount").value(0));

        assertThat(userSessionRepository.countByPrincipalName("alice")).isZero();

        mockMvc.perform(post("/api/admin/v1/users/" + user.getId() + "/disable")
                        .session(adminSession)
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
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private void ensureSessionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS spring_session (
                    primary_id CHAR(36) NOT NULL PRIMARY KEY,
                    session_id CHAR(36) NOT NULL,
                    creation_time BIGINT NOT NULL,
                    last_access_time BIGINT NOT NULL,
                    max_inactive_interval INT NOT NULL,
                    expiry_time BIGINT NOT NULL,
                    principal_name VARCHAR(100)
                )
                """);
        jdbcTemplate.execute("DELETE FROM spring_session");
    }

    private void insertSession(String principalName) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                """
                INSERT INTO spring_session (
                    primary_id, session_id, creation_time, last_access_time,
                    max_inactive_interval, expiry_time, principal_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, id, now, now, 3600, now + 3_600_000L, principalName
        );
    }

    private MockHttpSession adminLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                        "password", TEST_ADMIN_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
