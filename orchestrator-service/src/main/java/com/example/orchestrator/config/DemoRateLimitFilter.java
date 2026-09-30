package com.example.orchestrator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small global guardrail for the public portfolio environment. Cloud Run also
 * has max-instances=1 and a billing cap; this filter limits write traffic before
 * it reaches MongoDB. It is intentionally disabled for local load tests.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DemoRateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int requestsPerMinute;
    private final Clock clock;
    private final AtomicLong currentMinute = new AtomicLong(-1);
    private final AtomicInteger requests = new AtomicInteger();

    @Autowired
    public DemoRateLimitFilter(
            @Value("${demo.rate-limit.enabled:false}") boolean enabled,
            @Value("${demo.rate-limit.requests-per-minute:30}") int requestsPerMinute) {
        this(enabled, requestsPerMinute, Clock.systemUTC());
    }

    DemoRateLimitFilter(boolean enabled, int requestsPerMinute, Clock clock) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/payment-flows".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long minute = clock.instant().getEpochSecond() / 60;
        rotateWindow(minute);

        if (requests.incrementAndGet() > requestsPerMinute) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Limite do ambiente demonstrativo atingido; tente novamente em um minuto\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private synchronized void rotateWindow(long minute) {
        if (currentMinute.get() != minute) {
            currentMinute.set(minute);
            requests.set(0);
        }
    }
}
