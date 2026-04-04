package com.example.wms.auth.jwt;

import com.example.wms.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Stateless JWT utility — generates and validates HS256-signed tokens.
 *
 * <p>Uses the jjwt 0.12.x fluent API. The secret is read from
 * {@code app.jwt.secret} and must be at least 32 characters (256 bits)
 * so that {@link Keys#hmacShaKeyFor} accepts it for HS256.
 *
 * <p>Claim layout:
 * <ul>
 *   <li>{@code sub}  — username</li>
 *   <li>{@code role} — {@link Role} name, e.g. {@code WAREHOUSE_MANAGER}</li>
 *   <li>{@code iat}  — issued-at (epoch seconds, set by jjwt)</li>
 *   <li>{@code exp}  — expiry (epoch seconds)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtUtil {

    static final String CLAIM_ROLE = "role";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey secretKey;

    /** Called by Spring after dependency injection; also called directly in unit tests. */
    @PostConstruct
    void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a signed JWT for the given user.
     *
     * @param username the {@code sub} claim value
     * @param role     stored as the {@code role} claim
     * @return compact, URL-safe JWT string
     */
    public String generateToken(String username, Role role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)        // defaults to HS256 for SecretKey
                .compact();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} iff the token has a valid signature and has not expired.
     * All exception types from jjwt ({@link JwtException} and
     * {@link IllegalArgumentException}) are caught and logged at DEBUG level —
     * callers receive a clean boolean.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.debug("JWT token is null or empty: {}", ex.getMessage());
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Claim extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the {@code sub} (username) claim.
     *
     * @throws JwtException if the token is invalid or expired
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the {@code role} claim as a plain string
     * (e.g. {@code "WAREHOUSE_MANAGER"}).
     *
     * @throws JwtException if the token is invalid or expired
     */
    public String extractRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses and verifies the token, returning its {@link Claims} payload.
     * Throws a {@link JwtException} subclass on any failure (expired, tampered,
     * malformed, unsupported algorithm, etc.).
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
