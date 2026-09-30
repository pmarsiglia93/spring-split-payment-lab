package com.example.orchestrator.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class PaymentClient {
    private final RestClient restClient;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void create(String transactionId, BigDecimal amount, String idempotencyKey) {
        restClient.post().uri("/payments")
                .body(new CreatePaymentRequest(transactionId, amount, idempotencyKey))
                .retrieve().toBodilessEntity();
    }

    public void updateStatus(String transactionId, String status) {
        restClient.patch().uri("/payments/{transactionId}/status", transactionId)
                .body(Map.of("status", status))
                .retrieve().toBodilessEntity();
    }

    private record CreatePaymentRequest(
            String transactionId,
            BigDecimal amount,
            String idempotencyKey
    ) {
    }
}
