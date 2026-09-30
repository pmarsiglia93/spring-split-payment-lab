package com.example.orchestrator.service;

import com.example.orchestrator.client.PaymentClient;
import com.example.orchestrator.client.SplitClient;
import com.example.orchestrator.client.TransferClient;
import com.example.orchestrator.exception.IdempotencyConflictException;
import com.example.orchestrator.model.PaymentSaga;
import com.example.orchestrator.repository.PaymentSagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentFlowService {
    private static final Logger log = LoggerFactory.getLogger(PaymentFlowService.class);
    private final PaymentSagaRepository sagaRepository;
    private final PaymentClient paymentClient;
    private final SplitClient splitClient;
    private final TransferClient transferClient;

    public PaymentFlowService(PaymentSagaRepository sagaRepository, PaymentClient paymentClient,
                              SplitClient splitClient, TransferClient transferClient) {
        this.sagaRepository = sagaRepository;
        this.paymentClient = paymentClient;
        this.splitClient = splitClient;
        this.transferClient = transferClient;
    }

    public PaymentSaga start(String transactionId, BigDecimal amount, String idempotencyKey,
                             String simulation) {
        log.info("Starting payment Saga transactionId={} simulation={}", transactionId, simulation);
        PaymentSaga existing = findExisting(transactionId, amount, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        PaymentSaga saga;
        try {
            saga = sagaRepository.save(new PaymentSaga(
                    null, transactionId, idempotencyKey, amount, PaymentSaga.Status.PROCESSING,
                    PaymentSaga.Step.STARTED, null, correlationId, now, now, null
            ));
        } catch (DuplicateKeyException duplicateKeyException) {
            PaymentSaga winner = findExisting(transactionId, amount, idempotencyKey);
            if (winner == null) {
                throw duplicateKeyException;
            }
            log.info("Concurrent Saga already claimed transactionId={}", transactionId);
            return winner;
        }

        boolean transferAttempted = false;
        try {
            paymentClient.create(transactionId, amount, idempotencyKey);
            paymentClient.updateStatus(transactionId, "PROCESSING");
            saga = save(saga.advance(PaymentSaga.Status.PROCESSING,
                    PaymentSaga.Step.PAYMENT_CREATED, null));

            SplitClient.SplitResponse split = splitClient.calculate(transactionId, amount, simulation);
            saga = save(saga.advance(PaymentSaga.Status.PROCESSING,
                    PaymentSaga.Step.SPLIT_COMPLETED, null));

            transferAttempted = true;
            transferClient.create(transactionId, split, idempotencyKey, simulation);
            saga = save(saga.advance(PaymentSaga.Status.PROCESSING,
                    PaymentSaga.Step.TRANSFER_COMPLETED, null));

            paymentClient.updateStatus(transactionId, "COMPLETED");
            log.info("Payment Saga completed transactionId={}", transactionId);
            return save(saga.advance(PaymentSaga.Status.COMPLETED,
                    PaymentSaga.Step.TRANSFER_COMPLETED, null));
        } catch (RuntimeException exception) {
            return handleFailure(saga, transactionId, transferAttempted, exception);
        }
    }

    public PaymentSaga find(String transactionId) {
        return sagaRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + transactionId));
    }

    private PaymentSaga findExisting(String transactionId, BigDecimal amount, String idempotencyKey) {
        PaymentSaga byKey = sagaRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (byKey != null) {
            return validateSameRequest(byKey, transactionId, amount, idempotencyKey);
        }

        PaymentSaga byTransaction = sagaRepository.findByTransactionId(transactionId).orElse(null);
        if (byTransaction != null) {
            return validateSameRequest(byTransaction, transactionId, amount, idempotencyKey);
        }
        return null;
    }

    private PaymentSaga validateSameRequest(PaymentSaga existing, String transactionId,
                                            BigDecimal amount, String idempotencyKey) {
        boolean sameRequest = Objects.equals(existing.transactionId(), transactionId)
                && Objects.equals(existing.idempotencyKey(), idempotencyKey)
                && existing.amount().compareTo(amount) == 0;
        if (!sameRequest) {
            throw new IdempotencyConflictException();
        }
        return existing;
    }

    private PaymentSaga handleFailure(PaymentSaga saga, String transactionId,
                                      boolean transferAttempted, RuntimeException failure) {
        String reason = rootMessage(failure);
        log.warn("Payment Saga failed transactionId={} reason={}", transactionId, reason);
        if (transferAttempted) {
            try {
                transferClient.compensate(transactionId);
                safePaymentStatus(transactionId, "COMPENSATED");
                log.warn("Payment Saga compensated transactionId={}", transactionId);
                return save(saga.advance(PaymentSaga.Status.COMPENSATED,
                        PaymentSaga.Step.COMPENSATION_COMPLETED, reason));
            } catch (RuntimeException compensationFailure) {
                reason += " | Compensation failed: " + rootMessage(compensationFailure);
            }
        }
        safePaymentStatus(transactionId, "FAILED");
        return save(saga.advance(PaymentSaga.Status.FAILED, PaymentSaga.Step.FAILED, reason));
    }

    private void safePaymentStatus(String transactionId, String status) {
        try {
            paymentClient.updateStatus(transactionId, status);
        } catch (RuntimeException ignored) {
            // A Saga remains persisted for operational recovery if the status update also fails.
        }
    }

    private PaymentSaga save(PaymentSaga saga) {
        return sagaRepository.save(saga);
    }

    private String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
