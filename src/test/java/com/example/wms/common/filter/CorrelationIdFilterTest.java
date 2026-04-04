package com.example.wms.common.filter;

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
    void suppliedValidUuid_isEchoedBackInResponse() throws Exception {
        String cid     = UUID.randomUUID().toString();
        var    request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, cid);
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(cid);
    }

    @Test
    void noHeader_generatesNewCorrelationId() throws Exception {
        var request  = new MockHttpServletRequest();   // no header
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(UUID.fromString(
                response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))).isNotNull();
    }

    @Test
    void invalidUuidHeader_generatesNewCorrelationId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "not-a-uuid!!!");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(UUID.fromString(responseHeader)).isNotNull();
        assertThat(responseHeader).isNotEqualTo("not-a-uuid!!!");
    }
}
