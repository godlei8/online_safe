package com.godlei.onlinesafe.admin.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRepository;
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
class InvitationFlowIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inviteRepository.deleteAll();
        adminUserRepository.deleteAll();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(TEST_ADMIN_PASSWORD)));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void adminCanCreateListAndDeleteInvitations() throws Exception {
        MockHttpSession adminSession = adminLogin();

        MvcResult created = mockMvc.perform(post("/api/admin/v1/invitations")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "maxUses", 1,
                                "note", "首批用户"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plainCode").isNotEmpty())
                .andExpect(jsonPath("$.invitation.type").value("SINGLE"))
                .andExpect(jsonPath("$.invitation.status").value("ACTIVE"))
                .andReturn();

        String plainCode = objectMapper.readTree(created.getResponse().getContentAsString()).get("plainCode").asString();
        String inviteId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("invitation").get("id").asString();

        mockMvc.perform(get("/api/admin/v1/invitations/stats").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.active").value(1));

        mockMvc.perform(get("/api/admin/v1/invitations").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codeHint").isNotEmpty())
                .andExpect(jsonPath("$.content[0].plainCode").doesNotExist())
                .andExpect(jsonPath("$.content[0].creatorUsername").value("admin"));

        mockMvc.perform(get("/api/admin/v1/invitations/" + inviteId + "/plain-code").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plainCode").value(plainCode))
                .andExpect(jsonPath("$.codeHint").isNotEmpty());

        mockMvc.perform(post("/api/admin/v1/invitations")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("maxUses", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.id").value(org.hamcrest.Matchers.not(inviteId)));

        String secondId = inviteRepository.findAll().stream()
                .filter(invite -> !invite.getId().equals(inviteId))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(delete("/api/admin/v1/invitations/" + secondId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(inviteRepository.findById(secondId)).isEmpty();
        assertThat(inviteRepository.findById(inviteId)).isPresent();
    }

    private MockHttpSession adminLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/admin/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", TEST_ADMIN_PASSWORD
                        ))))
                .andExpect(status().isOk());
        return session;
    }
}
