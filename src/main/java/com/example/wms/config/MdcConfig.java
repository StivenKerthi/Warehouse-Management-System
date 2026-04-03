package com.example.wms.config;

import com.example.wms.common.filter.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers infrastructure filters.
 *
 * <p>{@link CorrelationIdFilter} is registered here as a servlet filter with
 * {@link Ordered#HIGHEST_PRECEDENCE} so it runs before every other filter —
 * including the Spring Security filter chain.
 *
 * <p>When {@code SecurityConfig} is written (TASK-011), it must also call
 * {@code http.addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)}
 * to ensure the correlation ID is present on the MDC when the JWT filter logs.
 */
@Configuration
public class MdcConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
            CorrelationIdFilter correlationIdFilter) {

        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(correlationIdFilter);

        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");   // applies to every request, including /actuator
        return registration;
    }
}
