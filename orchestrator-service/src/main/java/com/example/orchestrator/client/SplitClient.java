package com.example.orchestrator.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;

@Component
public class SplitClient {
    private final RestClient restClient;

    public SplitClient(@Qualifier("splitRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Retry(name = "splitService")
    @CircuitBreaker(name = "splitService")
    public SplitResponse calculate(String transactionId, BigDecimal amount, String simulation) {
        return restClient.post().uri("/splits")
                .body(new SplitRequest(transactionId, amount, simulation))
                .retrieve().body(SplitResponse.class);
    }

    public void ping() {
        restClient.get().uri("/actuator/health/readiness")
                .retrieve().toBodilessEntity();
    }

    private record SplitRequest(String transactionId, BigDecimal amount, String simulation) {
    }

    public record SplitResponse(String transactionId, BigDecimal sellerAmount, BigDecimal platformFee) {
    }
}
