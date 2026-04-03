package com.example.wms.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that assigns a correlation ID to every HTTP request.
 *
 * <p>Execution order: {@link Ordered#HIGHEST_PRECEDENCE} — runs before Spring
 * Security, so every log line produced during authentication already carries
 * the correlation ID.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>If the caller supplies an {@code X-Correlation-Id} request header, that
 *       value is reused (enables end-to-end tracing across service boundaries).</li>
 *   <li>Otherwise a new random UUID is generated.</li>
 *   <li>The value is placed in {@link MDC} under the key {@code correlationId}.</li>
 *   <li>The value is echoed back in the {@code X-Correlation-Id} response header.</li>
 *   <li>MDC is always cleared in a {@code finally} block to prevent leaking
 *       values across requests on pooled threads.</li>
 * </ol>
 *
 * <p>The {@code username} MDC field is populated separately by {@code JwtFilter}
 * after the JWT is validated (TASK-011).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_CORRELATION_KEY   = "correlationId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);

        try {
            MDC.put(MDC_CORRELATION_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            // Always clear MDC — Tomcat reuses threads; leftover values
            // from one request would bleed into the next request on the same thread.
            MDC.remove(MDC_CORRELATION_KEY);
        }
    }

    /**
     * Returns the caller-supplied correlation ID if it is a non-blank, valid
     * UUID-format string; otherwise generates a new random UUID.
     *
     * <p>Validation guards against header injection: we only accept values that
     * parse as a UUID — no arbitrary strings end up in logs.
     */
    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        if (StringUtils.hasText(incoming)) {
            try {
                // Validate format — UUID.fromString throws on invalid input
                UUID.fromString(incoming.trim());
                return incoming.trim();
            } catch (IllegalArgumentException ignored) {
                // Invalid format — generate a fresh one
            }
        }
        return UUID.randomUUID().toString();
    }
}
