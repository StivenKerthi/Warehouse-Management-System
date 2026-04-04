package com.example.wms.auth.controller;

import com.example.wms.auth.dto.LoginRequest;
import com.example.wms.auth.dto.LoginResponse;
import com.example.wms.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Both endpoints are publicly accessible (no JWT required) — see
 * {@code SecurityConfig} where {@code /api/auth/**} is mapped to {@code permitAll}.
 */
@Tag(name = "Authentication", description = "Login and logout operations")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/login
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Login",
        description = "Authenticates username and password. Returns a signed JWT to be sent " +
                      "as `Authorization: Bearer <token>` on all subsequent requests."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Authentication successful",
            content      = @Content(schema = @Schema(implementation = LoginResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "Validation failure — username or password is blank",
            content      = @Content(schema = @Schema())
        ),
        @ApiResponse(
            responseCode = "401",
            description  = "Invalid credentials or account disabled",
            content      = @Content(schema = @Schema())
        )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/logout
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Logout",
        description = "Stateless logout — the server holds no session state, so there is " +
                      "nothing to invalidate server-side. The client is responsible for " +
                      "discarding the JWT (e.g. clearing it from localStorage / memory). " +
                      "Tokens remain cryptographically valid until their `exp` claim is " +
                      "reached; if immediate revocation is required, implement a token " +
                      "denylist (e.g. Redis set of revoked JTIs) as a future enhancement."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description  = "Logout acknowledged — client must discard the token",
            content      = @Content(schema = @Schema())
        )
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Intentionally empty — this is a stateless API.
        // See @Operation description above for the rationale.
        return ResponseEntity.noContent().build();
    }
}
