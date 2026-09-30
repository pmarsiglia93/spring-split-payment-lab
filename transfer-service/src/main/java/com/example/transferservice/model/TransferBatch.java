package com.example.transferservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

@Document("transfer_batches")
public record TransferBatch(
        @Id String id,
        @Indexed(unique = true) String transactionId,
        BigDecimal sellerAmount,
        BigDecimal platformFee,
        TransferStatus status,
        String idempotencyKey,
        @Indexed(expireAfter = "7d") Instant createdAt
) {
    public TransferBatch compensate() {
        return new TransferBatch(id, transactionId, sellerAmount, platformFee,
                TransferStatus.COMPENSATED, idempotencyKey, createdAt);
    }
}
