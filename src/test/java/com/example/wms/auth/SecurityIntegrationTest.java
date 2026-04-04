package com.example.wms.auth;

import com.example.wms.auth.dto.LoginRequest;
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

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security integration tests — exercises the full filter/security chain with H2.
 *
 * The H2 JDBC URL uses INIT= to pre-create the user_role domain before
 * Hibernate's create-drop DDL runs, avoiding H2/PostgreSQL type incompatibility.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=" +
                "jdbc:h2:mem:wms-security-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;" +
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
class SecurityIntegrationTest {

    @Autowired private MockMvc        mockMvc;
    @Autowired private ObjectMapper   objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "P@ssw0rd!";

    @BeforeEach
    void seed() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username("test_client")
                .email("client@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.CLIENT)
                .active(true)
                .build());
    }

    /** Login and return the raw JWT string. */
    private String loginAndGetToken(String username) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD));
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void login_returnsTokenAndClientTokenOnManagerEndpointReturns403() throws Exception {
        // Happy: valid login returns 200 with token
        String body = objectMapper.writeValueAsString(new LoginRequest("test_client", PASSWORD));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(header().string("X-Correlation-Id", notNullValue()));

        // Bad: CLIENT token on manager endpoint → 403
        String token = loginAndGetToken("test_client");
        mockMvc.perform(get("/api/manager/inventory")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_returns401_andWrongPassword_returns401() throws Exception {
        // Happy baseline: unauthenticated request → 401
        mockMvc.perform(get("/api/manager/inventory"))
                .andExpect(status().isUnauthorized());

        // Bad: wrong password → 401
        String body = objectMapper.writeValueAsString(new LoginRequest("test_client", "wrong"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
