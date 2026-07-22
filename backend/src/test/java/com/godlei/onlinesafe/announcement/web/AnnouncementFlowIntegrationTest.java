package com.godlei.onlinesafe.announcement.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.announcement.infrastructure.AnnouncementReadRepository;
import com.godlei.onlinesafe.announcement.infrastructure.AnnouncementRepository;
import com.godlei.onlinesafe.auth.domain.AppUser;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AnnouncementFlowIntegrationTest {

    private static final String ADMIN_PASSWORD = "test-admin-password-123";
    private static final String USER_PASSWORD = "correct-password-123";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementReadRepository announcementReadRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        announcementReadRepository.deleteAll();
        announcementRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(ADMIN_PASSWORD)));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void publishThenUserSeesForceUnreadThenMarkReadClearsInbox() throws Exception {
        MockHttpSession adminSession = adminLogin();
        saveUser("13800138011", "alice");
        MockHttpSession userSession = userLogin("alice");

        MvcResult created = mockMvc.perform(post("/api/admin/v1/announcements")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsert("系统维护", "今晚维护一小时", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.pinned").value(true))
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/v1/announcements/inbox").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.latestUnread").doesNotExist())
                .andExpect(jsonPath("$.pinned").doesNotExist());

        mockMvc.perform(post("/api/admin/v1/announcements/" + id + "/publish")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/announcements/inbox").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.latestUnread.id").value(id))
                .andExpect(jsonPath("$.latestUnread.title").value("系统维护"))
                .andExpect(jsonPath("$.pinned.id").value(id));

        mockMvc.perform(get("/api/v1/announcements").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(post("/api/v1/announcements/" + id + "/read")
                        .session(userSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/announcements/inbox").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.latestUnread").doesNotExist())
                .andExpect(jsonPath("$.pinned.id").value(id))
                .andExpect(jsonPath("$.pinned.read").value(true));

        mockMvc.perform(put("/api/admin/v1/announcements/" + id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsert("系统维护（更新）", "改为两小时", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("系统维护（更新）"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());

        mockMvc.perform(get("/api/v1/announcements/inbox").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.pinned").doesNotExist());

        mockMvc.perform(post("/api/admin/v1/announcements/" + id + "/publish")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/announcements/inbox").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.latestUnread.title").value("系统维护（更新）"));

        mockMvc.perform(post("/api/admin/v1/announcements/" + id + "/offline")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        mockMvc.perform(get("/api/v1/announcements/inbox").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.pinned").doesNotExist());
    }

    private Map<String, Object> upsert(String title, String body, boolean pinned) {
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("title", title);
        bodyMap.put("body", body);
        bodyMap.put("pinned", pinned);
        bodyMap.put("startsAt", null);
        bodyMap.put("endsAt", null);
        return bodyMap;
    }

    private void saveUser(String phone, String username) {
        userRepository.save(AppUser.register(
                phone,
                username,
                username,
                passwordEncoder.encode(USER_PASSWORD)
        ));
    }

    private MockHttpSession adminLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/admin/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", ADMIN_PASSWORD
                        ))))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpSession userLogin(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", USER_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
