package com.example.paymentservice.service;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.exception.InvalidPaymentAmountException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void shouldCreatePaymentSuccessfully() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "transaction-123",
                new BigDecimal("150.00"),
                "key-123"
        );
        when(paymentRepository.findByTransactionId(request.transactionId()))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.create(request);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());

        Payment savedPayment = captor.getValue();
        assertSame(savedPayment, result);
        assertEquals(request.transactionId(), savedPayment.getTransactionId());
        assertEquals(request.amount(), savedPayment.getAmount());
        assertEquals(PaymentStatus.CREATED, savedPayment.getStatus());
        assertEquals(request.idempotencyKey(), savedPayment.getIdempotencyKey());
        assertNotNull(savedPayment.getCreatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void shouldRejectAmountLessThanOrEqualToZero(String amount) {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "transaction-123",
                new BigDecimal(amount),
                "key-123"
        );

        assertThrows(InvalidPaymentAmountException.class, () -> paymentService.create(request));

        verify(paymentRepository, never()).findByTransactionId(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingPaymentWhenTransactionIdAlreadyExists() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "transaction-123",
                new BigDecimal("150.00"),
                "new-key"
        );
        Payment existingPayment = new Payment(
                "payment-1",
                request.transactionId(),
                new BigDecimal("99.90"),
                PaymentStatus.CREATED,
                "original-key",
                Instant.parse("2026-01-01T10:00:00Z")
        );
        when(paymentRepository.findByTransactionId(request.transactionId()))
                .thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.create(request);

        assertSame(existingPayment, result);
        verify(paymentRepository, never()).save(any());
    }
}
