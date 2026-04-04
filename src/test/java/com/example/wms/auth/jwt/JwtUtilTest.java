package com.example.wms.auth.jwt;

import com.example.wms.user.model.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private static final String TEST_SECRET =
            "unit-test-secret-key-must-be-at-least-32-bytes!!";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 86_400_000L);
        jwtUtil.init();
    }

    @Test
    void generateAndValidate_roundTrip() {
        String token = jwtUtil.generateToken("alice", Role.CLIENT);

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("CLIENT");
    }

    @Test
    void nullToken_failsValidation() {
        // Covers the IllegalArgumentException catch branch in validateToken
        assertThat(jwtUtil.validateToken(null)).isFalse();
    }

    @Test
    void tamperedToken_failsValidation() {
        String token    = jwtUtil.generateToken("alice", Role.CLIENT);
        String tampered = token.substring(0, token.length() - 1) + "X";

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
        assertThatThrownBy(() -> jwtUtil.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }
}
