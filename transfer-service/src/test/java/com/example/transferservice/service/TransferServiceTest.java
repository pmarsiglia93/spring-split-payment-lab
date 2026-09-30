package com.example.transferservice.service;

import com.example.transferservice.exception.TransferSimulationException;
import com.example.transferservice.model.TransferBatch;
import com.example.transferservice.model.TransferStatus;
import com.example.transferservice.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock TransferRepository transferRepository;
    @InjectMocks TransferService transferService;

    @Test
    void shouldExposeFailureAfterPersistingTransfer() {
        when(transferRepository.findByTransactionId("tx-1")).thenReturn(Optional.empty());
        when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(TransferSimulationException.class, () -> transferService.create(
                "tx-1", new BigDecimal("90.00"), new BigDecimal("10.00"),
                "key-1", "TRANSFER_FAILURE"
        ));
    }

    @Test
    void shouldCompensateCompletedTransfer() {
        TransferBatch batch = new TransferBatch(
                "id-1", "tx-1", new BigDecimal("90.00"), new BigDecimal("10.00"),
                TransferStatus.COMPLETED, "key-1", Instant.now()
        );
        when(transferRepository.findByTransactionId("tx-1")).thenReturn(Optional.of(batch));
        when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferBatch result = transferService.compensate("tx-1");

        assertEquals(TransferStatus.COMPENSATED, result.status());
    }
}
