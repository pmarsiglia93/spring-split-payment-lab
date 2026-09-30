package com.example.transferservice.service;

import com.example.transferservice.exception.TransferSimulationException;
import com.example.transferservice.model.TransferBatch;
import com.example.transferservice.model.TransferStatus;
import com.example.transferservice.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;

@Service
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private final TransferRepository transferRepository;

    public TransferService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    public TransferBatch create(String transactionId, BigDecimal sellerAmount, BigDecimal platformFee,
                                String idempotencyKey, String simulation) {
        log.info("Creating transfers transactionId={} simulation={}", transactionId, simulation);
        return transferRepository.findByTransactionId(transactionId).orElseGet(() -> {
            TransferBatch batch = transferRepository.save(new TransferBatch(
                    null, transactionId, sellerAmount, platformFee,
                    TransferStatus.COMPLETED, idempotencyKey, Instant.now()
            ));
            if ("TRANSFER_FAILURE".equals(simulation)) {
                throw new TransferSimulationException();
            }
            return batch;
        });
    }

    public TransferBatch compensate(String transactionId) {
        String safeTransactionId = transactionId.replace('\n', '_').replace('\r', '_');
        log.warn("Compensating transfers transactionId={}", safeTransactionId);
        TransferBatch batch = find(transactionId);
        if (batch.status() == TransferStatus.COMPENSATED) {
            return batch;
        }
        return transferRepository.save(batch.compensate());
    }

    public TransferBatch find(String transactionId) {
        return transferRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transfer not found for transactionId: " + transactionId
                ));
    }
}
