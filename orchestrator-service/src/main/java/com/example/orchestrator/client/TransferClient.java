package com.example.orchestrator.client;

import com.example.orchestrator.client.SplitClient.SplitResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;

@Component
public class TransferClient {
    private final RestClient restClient;

    public TransferClient(@Qualifier("transferRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void create(String transactionId, SplitResponse split, String idempotencyKey, String simulation) {
        restClient.post().uri("/transfers")
                .body(new TransferRequest(transactionId, split.sellerAmount(), split.platformFee(),
                        idempotencyKey, simulation))
                .retrieve().toBodilessEntity();
    }

    public void compensate(String transactionId) {
        restClient.post().uri("/transfers/{transactionId}/compensate", transactionId)
                .retrieve().toBodilessEntity();
    }

    private record TransferRequest(
            String transactionId,
            BigDecimal sellerAmount,
            BigDecimal platformFee,
            String idempotencyKey,
            String simulation
    ) {
    }
}
