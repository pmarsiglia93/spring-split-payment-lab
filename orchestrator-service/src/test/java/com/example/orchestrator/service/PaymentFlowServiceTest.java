package com.example.orchestrator.service;

import com.example.orchestrator.client.PaymentClient;
import com.example.orchestrator.client.SplitClient;
import com.example.orchestrator.client.TransferClient;
import com.example.orchestrator.model.PaymentSaga;
import com.example.orchestrator.repository.PaymentSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        when(sagaRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(sagaRepository.findByTransactionId(any())).thenReturn(Optional.empty());
        when(sagaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCompleteSagaWhenEveryServiceSucceeds() {
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
}
