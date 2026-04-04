package com.example.wms.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless JWT authentication filter.
 *
 * <p>Runs once per request, after {@code CorrelationIdFilter} has already
 * placed the correlation ID into MDC.  Responsibilities:
 * <ol>
 *   <li>Extract the Bearer token from the {@code Authorization} header.</li>
 *   <li>Validate the token via {@link JwtUtil#validateToken}.</li>
 *   <li>On success: build a fully-authenticated
 *       {@link UsernamePasswordAuthenticationToken} and place it in the
 *       {@link SecurityContextHolder} so downstream controllers can use
 *       {@code @AuthenticationPrincipal} or {@code SecurityContextHolder.getContext()}.</li>
 *   <li>Put {@code username} into MDC so every log line for this request
 *       shows the authenticated user (logback pattern: {@code [usr=%X{username:-anon}]}).</li>
 *   <li>Clear the {@code username} MDC entry in {@code finally} to prevent
 *       thread-pool leakage between requests.</li>
 * </ol>
 *
 * <p><strong>Not annotated with {@code @Component}.</strong>  If it were,
 * Spring Boot would register it as a standalone servlet filter AND SecurityConfig
 * would add it to the security chain — running it twice per request.  Instead it
 * is instantiated once as a bean inside {@code SecurityConfig}.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    /** MDC key consumed by {@code logback-spring.xml}: {@code [usr=%X{username:-anon}]}. */
    public static final String MDC_USERNAME_KEY = "username";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token != null && jwtUtil.validateToken(token)) {
            String username = jwtUtil.extractUsername(token);
            String role     = jwtUtil.extractRole(token);

            // Build the authority with Spring Security's expected ROLE_ prefix.
            // JwtUtil stores "WAREHOUSE_MANAGER"; authority becomes "ROLE_WAREHOUSE_MANAGER".
            var authority = new SimpleGrantedAuthority("ROLE_" + role);

            var authentication = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(authority));
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            MDC.put(MDC_USERNAME_KEY, username);
            log.debug("JWT authenticated: user={}, role={}, path={}",
                    username, role, request.getRequestURI());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CorrelationIdFilter owns correlationId MDC cleanup.
            // JwtFilter owns only the username key.
            MDC.remove(MDC_USERNAME_KEY);
            // Clear the SecurityContext — defensive measure for thread pool reuse.
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Extracts the raw JWT string from the {@code Authorization: Bearer <token>} header.
     *
     * @return the token string, or {@code null} if the header is absent or malformed
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
