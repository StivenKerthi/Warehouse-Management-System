package com.example.wms.user;

import com.example.wms.auth.dto.LoginRequest;
import com.example.wms.user.dto.AssignRoleRequest;
import com.example.wms.user.dto.CreateUserRequest;
import com.example.wms.user.dto.UpdateUserRequest;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=" +
                "jdbc:h2:mem:wms-user-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;" +
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
class AdminUserControllerTest {

    @Autowired private MockMvc         mockMvc;
    @Autowired private ObjectMapper    objectMapper;
    @Autowired private UserRepository  userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String ADMIN_PASSWORD = "Admin@1234";
    private String adminToken;
    private UUID   targetUserId;

    @BeforeEach
    void seed() throws Exception {
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("admin")
                .email("admin@wms.com")
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(Role.SYSTEM_ADMIN)
                .active(true)
                .build());

        User target = userRepository.save(User.builder()
                .username("target_user")
                .email("target@wms.com")
                .passwordHash(passwordEncoder.encode("Target@123"))
                .role(Role.CLIENT)
                .active(true)
                .build());
        targetUserId = target.getId();

        adminToken = loginAndGetToken("admin");
    }

    @Test
    void createUser_returns201WithUserData() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "new_user", "new@wms.com", "Password1!", Role.CLIENT);

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("new_user"))
                .andExpect(jsonPath("$.data.role").value("CLIENT"));
    }

    @Test
    void listUsers_returns200WithPagedContent() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getUser_returns200WithUserData() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + targetUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(targetUserId.toString()))
                .andExpect(jsonPath("$.data.username").value("target_user"));
    }

    @Test
    void updateUser_returns200WithUpdatedEmail() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("updated@wms.com", null, null);

        mockMvc.perform(put("/api/admin/users/" + targetUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("updated@wms.com"));
    }

    @Test
    void deleteUser_returns204AndUserIsDeactivated() throws Exception {
        mockMvc.perform(delete("/api/admin/users/" + targetUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void assignRole_returns200WithNewRole() throws Exception {
        AssignRoleRequest request = new AssignRoleRequest(Role.WAREHOUSE_MANAGER);

        mockMvc.perform(put("/api/admin/users/" + targetUserId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("WAREHOUSE_MANAGER"));
    }

    private String loginAndGetToken(String username) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, ADMIN_PASSWORD));
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }
}
