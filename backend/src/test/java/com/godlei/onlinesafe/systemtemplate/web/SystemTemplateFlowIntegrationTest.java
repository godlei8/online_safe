package com.godlei.onlinesafe.systemtemplate.web;

import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.systemtemplate.infrastructure.SystemTemplateRepository;
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
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SystemTemplateFlowIntegrationTest {

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
    private SystemTemplateRepository systemTemplateRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        systemTemplateRepository.deleteAll();
        userRepository.deleteAll();
        adminUserRepository.deleteAll();
        adminUserRepository.save(AdminUser.createActive("admin", passwordEncoder.encode(ADMIN_PASSWORD)));
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void publishMakesVisibleThenOfflineHidesAndDraftDeleteWorks() throws Exception {
        MockHttpSession adminSession = adminLogin();
        saveUser("13800138021", "bob");
        MockHttpSession userSession = userLogin("bob");

        mockMvc.perform(post("/api/admin/v1/system-templates")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsert("", "ChatGPT", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        MvcResult created = mockMvc.perform(post("/api/admin/v1/system-templates")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsert("通用登录", "ChatGPT", List.of(
                                field("自定义备注", "TEXT")
                        )))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.platform").value("ChatGPT"))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/v1/system-templates").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/system-templates/" + id).session(userSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SYSTEM_TEMPLATE_NOT_FOUND"));

        mockMvc.perform(post("/api/admin/v1/system-templates/" + id + "/publish")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/system-templates").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("通用登录"));

        mockMvc.perform(put("/api/admin/v1/system-templates/" + id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsert("通用登录（修订）", "ChatGPT", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.name").value("通用登录（修订）"));

        mockMvc.perform(get("/api/v1/system-templates").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/system-templates/" + id).session(userSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/v1/system-templates/" + id + "/publish")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.name").value("通用登录（修订）"));

        mockMvc.perform(get("/api/v1/system-templates/" + id).session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("通用登录（修订）"));

        mockMvc.perform(patch("/api/admin/v1/system-templates/" + id + "/sort")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(delete("/api/admin/v1/system-templates/" + id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYSTEM_TEMPLATE_INVALID_STATUS"));

        mockMvc.perform(post("/api/admin/v1/system-templates/" + id + "/offline")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        mockMvc.perform(get("/api/v1/system-templates").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        MvcResult draft = mockMvc.perform(post("/api/admin/v1/system-templates")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsert("待删草稿", "Claude", List.of()))))
                .andExpect(status().isCreated())
                .andReturn();
        String draftId = objectMapper.readTree(draft.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(delete("/api/admin/v1/system-templates/" + draftId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private Map<String, Object> upsert(String name, String platform, List<Map<String, Object>> extraFields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("platform", platform);
        body.put("channel", "官方");
        body.put("channelUrl", "https://example.com");
        body.put("fields", extraFields);
        body.put("sortOrder", 1);
        return body;
    }

    private Map<String, Object> field(String name, String type) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", UUID.randomUUID().toString());
        field.put("name", name);
        field.put("type", type);
        field.put("value", "should-be-stripped");
        field.put("required", false);
        field.put("sensitive", false);
        field.put("copyable", true);
        field.put("hint", "");
        field.put("order", 2);
        return field;
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
