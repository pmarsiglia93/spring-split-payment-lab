package com.example.paymentservice.service;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.exception.InvalidPaymentAmountException;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment create(CreatePaymentRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentAmountException();
        }

        return paymentRepository.findByTransactionId(request.transactionId())
                .orElseGet(() -> paymentRepository.save(new Payment(
                        null,
                        request.transactionId(),
                        request.amount(),
                        PaymentStatus.CREATED,
                        request.idempotencyKey(),
                        Instant.now()
                )));
    }

    public Payment findByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException(transactionId));
    }

    public Payment updateStatus(String transactionId, PaymentStatus status) {
        Payment payment = findByTransactionId(transactionId);
        payment.updateStatus(status);
        return paymentRepository.save(payment);
    }
}
