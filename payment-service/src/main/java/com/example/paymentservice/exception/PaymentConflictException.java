package com.example.paymentservice.exception;

public class PaymentConflictException extends RuntimeException {

    public PaymentConflictException() {
        super("Transaction or idempotency key was already used with different payment data");
    }
}
