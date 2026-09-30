package com.example.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DemoRateLimitFilterTest {

    @Test
    void isCreatedBySpringWithConfigurationProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(DemoRateLimitFilter.class)
                .withPropertyValues(
                        "demo.rate-limit.enabled=true",
                        "demo.rate-limit.requests-per-minute=30")
                .run(context -> assertThat(context).hasSingleBean(DemoRateLimitFilter.class));
    }

    @Test
    void rejectsWritesBeyondTheConfiguredDemoLimit() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
        DemoRateLimitFilter filter = new DemoRateLimitFilter(true, 1, clock);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment-flows");
        var chain = mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request, rejected, chain);

        verify(chain).doFilter(eq(request), any());
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("ambiente demonstrativo");
    }
}
