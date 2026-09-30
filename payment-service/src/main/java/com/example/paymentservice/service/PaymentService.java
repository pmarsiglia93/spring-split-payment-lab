package com.example.paymentservice.service;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.exception.InvalidPaymentAmountException;
import com.example.paymentservice.exception.PaymentConflictException;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

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

        Payment existing = findExisting(request);
        if (existing != null) {
            return validateSameRequest(existing, request);
        }

        Payment candidate = new Payment(
                null,
                request.transactionId(),
                request.amount(),
                PaymentStatus.CREATED,
                request.idempotencyKey(),
                Instant.now()
        );

        try {
            return paymentRepository.save(candidate);
        } catch (DuplicateKeyException duplicateKeyException) {
            Payment winner = findExisting(request);
            if (winner == null) {
                throw duplicateKeyException;
            }
            return validateSameRequest(winner, request);
        }
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

    private Payment findExisting(CreatePaymentRequest request) {
        return paymentRepository.findByTransactionId(request.transactionId())
                .or(() -> paymentRepository.findByIdempotencyKey(request.idempotencyKey()))
                .orElse(null);
    }

    private Payment validateSameRequest(Payment existing, CreatePaymentRequest request) {
        boolean sameRequest = Objects.equals(existing.getTransactionId(), request.transactionId())
                && Objects.equals(existing.getIdempotencyKey(), request.idempotencyKey())
                && existing.getAmount().compareTo(request.amount()) == 0;
        if (!sameRequest) {
            throw new PaymentConflictException();
        }
        return existing;
    }
}
