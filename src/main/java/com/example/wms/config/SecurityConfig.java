package com.example.wms.config;

import com.example.wms.auth.jwt.JwtFilter;
import com.example.wms.auth.jwt.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><strong>CSRF disabled</strong> — stateless JWT API; no session cookies.</li>
 *   <li><strong>STATELESS session</strong> — Spring Security never creates an
 *       {@code HttpSession}; every request is authenticated from the JWT alone.</li>
 *   <li><strong>JwtFilter registration</strong> — created here as a plain object
 *       (not a {@code @Component}) to avoid the double-registration problem:
 *       a {@code @Component} filter is auto-registered by Spring Boot as a servlet
 *       filter AND added to the security chain by {@code addFilterBefore}, running
 *       it twice per request.</li>
 *   <li><strong>CorrelationIdFilter not re-added here</strong> — it is registered
 *       as a servlet filter at {@code HIGHEST_PRECEDENCE} in {@link MdcConfig},
 *       which means it runs before Spring Security's {@code DelegatingFilterProxy}.
 *       Adding it to the security chain again would re-generate a different
 *       correlation ID and corrupt MDC tracing.</li>
 *   <li><strong>{@code @EnableMethodSecurity}</strong> — enables
 *       {@code @PreAuthorize} / {@code @PostAuthorize} for fine-grained access
 *       control in service/controller methods (e.g. ownership checks in
 *       {@code OrderService}).</li>
 * </ul>
 *
 * <h2>URL access rules (in evaluation order)</h2>
 * <pre>
 *   /api/auth/**          → permitAll
 *   /swagger-ui/**        → permitAll
 *   /v3/api-docs/**       → permitAll
 *   /actuator/health      → permitAll
 *   /api/admin/**         → SYSTEM_ADMIN only
 *   /api/manager/**       → WAREHOUSE_MANAGER only
 *   /api/orders/**        → CLIENT only
 *   (anything else)       → authenticated
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // ── Public ──────────────────────────────────────────────────
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // ── Role-protected (most specific prefixes first) ────────────
                .requestMatchers("/api/admin/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/api/manager/**").hasRole("WAREHOUSE_MANAGER")
                .requestMatchers("/api/orders/**").hasRole("CLIENT")

                // ── Catch-all ────────────────────────────────────────────────
                .anyRequest().authenticated()
            )

            .exceptionHandling(ex -> ex
                // 401 — missing or invalid JWT
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\"," +
                        "\"message\":\"Authentication required\"}");
                })
                // 403 — authenticated but wrong role
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        "{\"status\":403,\"error\":\"Forbidden\"," +
                        "\"message\":\"You do not have permission to access this resource\"}");
                })
            )

            // Run JWT authentication before Spring's username/password filter.
            // JwtFilter is instantiated directly — see class-level javadoc.
            .addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder — used by {@code AuthService} to verify passwords
     * and by {@code UserService} to hash new passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean so {@code AuthService}
     * can call {@code authenticate()} directly without Spring Security's
     * form-login machinery.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
