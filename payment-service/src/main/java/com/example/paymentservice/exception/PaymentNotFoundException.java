package com.example.paymentservice.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String transactionId) {
        super("Payment not found for transactionId: " + transactionId);
    }
}
