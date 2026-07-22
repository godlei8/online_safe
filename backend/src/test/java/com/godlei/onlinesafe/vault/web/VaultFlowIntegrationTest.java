package com.godlei.onlinesafe.vault.web;

import tools.jackson.databind.ObjectMapper;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class VaultFlowIntegrationTest {

    private static final String PASSWORD = "correct-password-123";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VaultItemRepository itemRepository;

    @Autowired
    private PrivateTemplateRepository templateRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        templateRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void itemAndTemplateFlowWithOwnerIsolationAndServerEncryption() throws Exception {
        saveUser("13800138001", "alice");
        saveUser("13800138002", "bob");
        MockHttpSession aliceSession = login("alice");
        MockHttpSession bobSession = login("bob");

        String itemId = UUID.randomUUID().toString();
        Map<String, Object> itemCreate = sampleItem(itemId, 0L, "初始密码");
        mockMvc.perform(post("/api/v1/vault/items")
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemCreate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.payload.name").value("工作邮箱"))
                .andExpect(jsonPath("$.payload.fields[1].value").value("初始密码"));

        var stored = itemRepository.findById(itemId).orElseThrow();
        String cipherText = new String(stored.getCiphertext());
        assertThat(cipherText).doesNotContain("初始密码");
        assertThat(stored.getNonce()).hasSize(12);
        assertThat(stored.getAlgoVersion()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/vault/items/" + itemId).session(bobSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/vault/items").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        Map<String, Object> itemUpdate = sampleItem(itemId, 0L, "更新后密码");
        mockMvc.perform(put("/api/v1/vault/items/" + itemId)
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.payload.fields[1].value").value("更新后密码"));

        mockMvc.perform(put("/api/v1/vault/items/" + itemId)
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleItem(itemId, 0L, "冲突"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAULT_REVISION_CONFLICT"));

        String templateId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/vault/private-templates")
                        .session(aliceSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTemplate(templateId, 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.name").value("常用登录"));

        mockMvc.perform(get("/api/v1/vault/private-templates/" + templateId).session(bobSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/vault/items/" + itemId)
                        .session(aliceSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/vault/items/" + itemId).session(aliceSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/vault/private-templates/" + templateId)
                        .session(aliceSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private AppUser saveUser(String phone, String username) {
        return userRepository.save(AppUser.register(
                phone,
                username,
                username,
                passwordEncoder.encode(PASSWORD)
        ));
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static Map<String, Object> sampleItem(String id, long revision, String password) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "工作邮箱");
        payload.put("platform", "Google");
        payload.put("channel", "自行注册");
        payload.put("status", "NORMAL");
        payload.put("expiresAt", null);
        payload.put("tags", List.of());
        payload.put("notes", "");
        payload.put("templateSnapshot", null);
        payload.put("fields", List.of(
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "name", "账号",
                        "type", "TEXT",
                        "value", "a@example.com",
                        "required", true,
                        "sensitive", false,
                        "copyable", true,
                        "hint", "",
                        "order", 0,
                        "systemKey", "account"
                ),
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "name", "密码",
                        "type", "PASSWORD",
                        "value", password,
                        "required", true,
                        "sensitive", true,
                        "copyable", true,
                        "hint", "",
                        "order", 1,
                        "systemKey", "password"
                )
        ));
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("payload", payload);
        body.put("revision", revision);
        return body;
    }

    private static Map<String, Object> sampleTemplate(String id, long revision) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "常用登录");
        payload.put("platform", "通用");
        payload.put("channel", "");
        payload.put("fields", List.of(
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "name", "账号",
                        "type", "TEXT",
                        "required", true,
                        "sensitive", false,
                        "copyable", true,
                        "hint", "",
                        "order", 0
                )
        ));
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("payload", payload);
        body.put("revision", revision);
        return body;
    }
}
