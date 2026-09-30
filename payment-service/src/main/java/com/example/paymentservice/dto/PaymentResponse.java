package com.example.paymentservice.dto;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String transactionId,
        BigDecimal amount,
        PaymentStatus status,
        String idempotencyKey,
        Instant createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getTransactionId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt()
        );
    }
}
