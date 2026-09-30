package com.example.paymentservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String transactionId;

    private BigDecimal amount;
    private PaymentStatus status;

    @Indexed(unique = true)
    private String idempotencyKey;

    @Indexed(expireAfter = "7d")
    private Instant createdAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public Payment(
            String id,
            String transactionId,
            BigDecimal amount,
            PaymentStatus status,
            String idempotencyKey,
            Instant createdAt
    ) {
        this.id = id;
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = status;
    }
}
