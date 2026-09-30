package com.example.orchestrator.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResilienceStatusController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public ResilienceStatusController(CircuitBreakerRegistry circuitBreakerRegistry,
                                      RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    @GetMapping("/api/lab/resilience/split")
    public ResilienceSnapshot splitStatus() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("splitService");
        CircuitBreaker.Metrics circuitMetrics = circuitBreaker.getMetrics();
        Retry.Metrics retryMetrics = retryRegistry.retry("splitService").getMetrics();

        return new ResilienceSnapshot(
                circuitBreaker.getState().name(),
                circuitMetrics.getFailureRate(),
                circuitMetrics.getNumberOfBufferedCalls(),
                circuitMetrics.getNumberOfSuccessfulCalls(),
                circuitMetrics.getNumberOfFailedCalls(),
                circuitMetrics.getNumberOfNotPermittedCalls(),
                retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                retryMetrics.getNumberOfFailedCallsWithRetryAttempt()
        );
    }

    public record ResilienceSnapshot(
            String circuitState,
            float failureRate,
            int bufferedCalls,
            int successfulCalls,
            int failedCalls,
            long notPermittedCalls,
            long successfulCallsWithRetry,
            long failedCallsWithRetry
    ) {
    }
}
