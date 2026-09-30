package com.example.paymentservice.exception;

public class InvalidPaymentAmountException extends RuntimeException {

    public InvalidPaymentAmountException() {
        super("Payment amount must be greater than zero");
    }
}
