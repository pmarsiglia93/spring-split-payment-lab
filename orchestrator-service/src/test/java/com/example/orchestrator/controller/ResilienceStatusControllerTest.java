package com.example.orchestrator.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceStatusControllerTest {

    @Test
    void shouldExposeOnlySanitizedResilienceCounters() {
        ResilienceStatusController controller = new ResilienceStatusController(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults()
        );

        ResilienceStatusController.ResilienceSnapshot result = controller.splitStatus();

        assertThat(result.circuitState()).isEqualTo("CLOSED");
        assertThat(result.bufferedCalls()).isZero();
        assertThat(result.failedCalls()).isZero();
        assertThat(result.notPermittedCalls()).isZero();
    }
}
