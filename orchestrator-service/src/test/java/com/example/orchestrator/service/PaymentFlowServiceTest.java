package com.example.orchestrator.service;

import com.example.orchestrator.client.PaymentClient;
import com.example.orchestrator.client.SplitClient;
import com.example.orchestrator.client.TransferClient;
import com.example.orchestrator.exception.IdempotencyConflictException;
import com.example.orchestrator.model.PaymentSaga;
import com.example.orchestrator.repository.PaymentSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFlowServiceTest {
    @Mock PaymentSagaRepository sagaRepository;
    @Mock PaymentClient paymentClient;
    @Mock SplitClient splitClient;
    @Mock TransferClient transferClient;
    private PaymentFlowService service;

    @BeforeEach
    void setUp() {
        service = new PaymentFlowService(sagaRepository, paymentClient, splitClient, transferClient);
    }

    @Test
    void shouldCompleteSagaWhenEveryServiceSucceeds() {
        stubNewSaga();
        SplitClient.SplitResponse split = new SplitClient.SplitResponse(
                "tx-1", new BigDecimal("90.00"), new BigDecimal("10.00")
        );
        when(splitClient.calculate("tx-1", new BigDecimal("100.00"), "NONE")).thenReturn(split);

        PaymentSaga result = service.start("tx-1", new BigDecimal("100.00"), "key-1", "NONE");

        assertEquals(PaymentSaga.Status.COMPLETED, result.status());
        assertEquals(PaymentSaga.Step.TRANSFER_COMPLETED, result.currentStep());
        verify(paymentClient).updateStatus("tx-1", "COMPLETED");
        verify(transferClient, never()).compensate(any());
    }

    @Test
    void shouldCompensateWhenTransferFailsAfterBeingAttempted() {
        stubNewSaga();
        SplitClient.SplitResponse split = new SplitClient.SplitResponse(
                "tx-2", new BigDecimal("90.00"), new BigDecimal("10.00")
        );
        when(splitClient.calculate("tx-2", new BigDecimal("100.00"), "TRANSFER_FAILURE"))
                .thenReturn(split);
        doThrow(new RuntimeException("simulated transfer failure"))
                .when(transferClient).create("tx-2", split, "key-2", "TRANSFER_FAILURE");

        PaymentSaga result = service.start(
                "tx-2", new BigDecimal("100.00"), "key-2", "TRANSFER_FAILURE"
        );

        assertEquals(PaymentSaga.Status.COMPENSATED, result.status());
        assertEquals(PaymentSaga.Step.COMPENSATION_COMPLETED, result.currentStep());
        verify(transferClient).compensate("tx-2");
        verify(paymentClient).updateStatus("tx-2", "COMPENSATED");
    }

    @Test
    void shouldReturnWinningSagaWhenConcurrentInsertLosesTheRace() {
        PaymentSaga winner = saga("saga-winner", "tx-race", "key-race", "100.00");
        when(sagaRepository.findByIdempotencyKey("key-race"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(sagaRepository.findByTransactionId("tx-race")).thenReturn(Optional.empty());
        when(sagaRepository.save(any())).thenThrow(new DuplicateKeyException("concurrent insert"));

        PaymentSaga result = service.start(
                "tx-race", new BigDecimal("100.00"), "key-race", "NONE"
        );

        assertSame(winner, result);
        verifyNoInteractions(paymentClient, splitClient, transferClient);
    }

    @Test
    void shouldRejectTransactionReusedWithDifferentData() {
        PaymentSaga existing = saga("saga-1", "tx-1", "original-key", "100.00");
        when(sagaRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(sagaRepository.findByTransactionId("tx-1")).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () ->
                service.start("tx-1", new BigDecimal("999.00"), "new-key", "NONE"));

        verify(sagaRepository, never()).save(any());
        verifyNoInteractions(paymentClient, splitClient, transferClient);
    }

    private void stubNewSaga() {
        when(sagaRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(sagaRepository.findByTransactionId(any())).thenReturn(Optional.empty());
        when(sagaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PaymentSaga saga(String id, String transactionId, String key, String amount) {
        Instant now = Instant.parse("2026-09-30T10:00:00Z");
        return new PaymentSaga(
                id,
                transactionId,
                key,
                new BigDecimal(amount),
                PaymentSaga.Status.PROCESSING,
                PaymentSaga.Step.STARTED,
                null,
                "correlation-test",
                now,
                now,
                0L
        );
    }
}
