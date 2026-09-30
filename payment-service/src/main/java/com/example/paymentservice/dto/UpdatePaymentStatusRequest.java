package com.example.paymentservice.dto;

import com.example.paymentservice.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdatePaymentStatusRequest(
        @NotNull(message = "status is required") PaymentStatus status
) {
}
