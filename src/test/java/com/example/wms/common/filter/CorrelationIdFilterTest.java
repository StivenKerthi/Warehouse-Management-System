package com.example.wms.common.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesNewCorrelationId_whenHeaderAbsent() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotBlank();
        assertThat(UUID.fromString(responseHeader)).isNotNull(); // valid UUID
    }

    @Test
    void reusesCorrelationId_whenValidUuidHeaderPresent() throws Exception {
        String incomingId = UUID.randomUUID().toString();
        var request  = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, incomingId);
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(incomingId);
    }

    @Test
    void generatesNewCorrelationId_whenHeaderIsInvalidUuid() throws Exception {
        var request  = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "not-a-uuid!!!");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(UUID.fromString(responseHeader)).isNotNull(); // a valid UUID was generated
        assertThat(responseHeader).isNotEqualTo("not-a-uuid!!!");
    }

    @Test
    void populatesMdc_duringFilterExecution() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        // FilterChain is a functional interface — use a lambda to inspect MDC mid-chain
        final String[] captured = {null};
        FilterChain capturingChain = (req, res) ->
                captured[0] = MDC.get(CorrelationIdFilter.MDC_CORRELATION_KEY);

        filter.doFilterInternal(request, response, capturingChain);

        assertThat(captured[0]).isNotBlank();
        assertThat(UUID.fromString(captured[0])).isNotNull();
    }

    @Test
    void clearsMdc_afterRequestCompletes() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        // Key must be gone after the filter — no bleed into the next request on the same thread
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_KEY)).isNull();
    }

    @Test
    void clearsMdc_evenWhenFilterChainThrows() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        // FilterChain lambda that simulates a downstream failure
        FilterChain throwingChain = (req, res) -> { throw new RuntimeException("downstream failure"); };

        try {
            filter.doFilterInternal(request, response, throwingChain);
        } catch (RuntimeException ignored) {
            // expected — we only care that MDC is clean afterward
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_KEY)).isNull();
    }
}
