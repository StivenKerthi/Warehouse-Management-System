package com.example.wms.config;

import com.example.wms.auth.dto.LoginRequest;
import com.example.wms.config.dto.UpdateConfigRequest;
import com.example.wms.config.model.SystemConfig;
import com.example.wms.config.repository.SystemConfigRepository;
import com.example.wms.user.model.Role;
import com.example.wms.user.model.User;
import com.example.wms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=" +
                "jdbc:h2:mem:wms-config-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;" +
                "INIT=CREATE DOMAIN IF NOT EXISTS user_role AS VARCHAR(50)",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class AdminConfigControllerTest {

    @Autowired private MockMvc                  mockMvc;
    @Autowired private ObjectMapper             objectMapper;
    @Autowired private UserRepository           userRepository;
    @Autowired private SystemConfigRepository   configRepository;
    @Autowired private PasswordEncoder          passwordEncoder;

    private static final String ADMIN_PASSWORD = "Admin@1234";
    private String adminToken;

    @BeforeEach
    void seed() throws Exception {
        configRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("admin")
                .email("admin@wms.com")
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(Role.SYSTEM_ADMIN)
                .active(true)
                .build());

        SystemConfig config = new SystemConfig();
        config.setConfigKey("delivery_window_days");
        config.setConfigValue("14");
        config.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        configRepository.save(config);

        adminToken = loginAndGetToken();
    }

    @Test
    void getConfig_returns200WithAllEntries() throws Exception {
        mockMvc.perform(get("/api/admin/config")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].key").value("delivery_window_days"))
                .andExpect(jsonPath("$.data[0].value").value("14"));
    }

    @Test
    void updateConfig_returns200WithUpdatedValue() throws Exception {
        UpdateConfigRequest request = new UpdateConfigRequest("delivery_window_days", "21");

        mockMvc.perform(put("/api/admin/config")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("delivery_window_days"))
                .andExpect(jsonPath("$.data.value").value("21"))
                .andExpect(jsonPath("$.data.updatedBy").value("admin"));
    }

    private String loginAndGetToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", ADMIN_PASSWORD));
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }
}
