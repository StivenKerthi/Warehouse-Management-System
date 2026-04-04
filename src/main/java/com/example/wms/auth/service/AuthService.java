package com.example.wms.auth.service;

import com.example.wms.auth.dto.LoginRequest;
import com.example.wms.auth.dto.LoginResponse;
import com.example.wms.auth.jwt.JwtUtil;
import com.example.wms.user.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles credential verification and JWT issuance.
 *
 * <p>Delegates all password checking to Spring Security's
 * {@link AuthenticationManager} (backed by {@code UserDetailsServiceImpl} +
 * {@code BCryptPasswordEncoder}).  On success, the authenticated
 * {@link Authentication} object already carries the canonical username and
 * authority so a second DB lookup is unnecessary.
 *
 * <p>On any {@link AuthenticationException} (bad password, disabled account,
 * locked account, etc.) a {@code 401 Unauthorized} is returned.  The error
 * message is deliberately generic to prevent username enumeration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil               jwtUtil;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Authenticates the supplied credentials and returns a signed JWT.
     *
     * @param request username + password
     * @return {@link LoginResponse} with the JWT and metadata
     * @throws ResponseStatusException 401 if credentials are invalid or account is inactive
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));
        } catch (AuthenticationException ex) {
            // Log at WARN (not ERROR — bad credentials are expected noise, not bugs).
            // Generic message deliberately avoids confirming whether the username exists.
            log.warn("Login failed for username='{}': {}", request.username(), ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // Extract canonical username (as stored in DB) and role authority from the
        // authenticated principal.  No second DB query needed.
        String username      = authentication.getName();
        String roleAuthority = authentication.getAuthorities()
                .iterator().next()
                .getAuthority();                               // e.g. "ROLE_WAREHOUSE_MANAGER"
        Role role = Role.valueOf(roleAuthority.substring("ROLE_".length()));

        String token = jwtUtil.generateToken(username, role);
        log.info("Login successful: username='{}', role='{}'", username, role);

        return new LoginResponse(
                token,
                "Bearer",
                username,
                role.name(),
                expirationMs / 1000   // return seconds — standard OAuth2 convention
        );
    }
}
