package com.example.orchestrator.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document("payment_sagas")
public record PaymentSaga(
        @Id String id,
        @Indexed(unique = true) String transactionId,
        @Indexed(unique = true) String idempotencyKey,
        BigDecimal amount,
        Status status,
        Step currentStep,
        String failureReason,
        String correlationId,
        @Indexed(expireAfter = "7d") Instant createdAt,
        Instant updatedAt,
        @Version Long version
) {
    public PaymentSaga advance(Status newStatus, Step newStep, String reason) {
        return new PaymentSaga(id, transactionId, idempotencyKey, amount, newStatus,
                newStep, reason, correlationId, createdAt, Instant.now(), version);
    }

    public enum Status {
        PROCESSING, COMPLETED, FAILED, COMPENSATED
    }

    public enum Step {
        STARTED, PAYMENT_CREATED, SPLIT_COMPLETED, TRANSFER_COMPLETED,
        COMPENSATION_COMPLETED, FAILED
    }
}
