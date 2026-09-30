package com.example.orchestrator.repository;

import com.example.orchestrator.model.PaymentSaga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
class PaymentSagaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    @Autowired
    private PaymentSagaRepository sagaRepository;

    @BeforeEach
    void cleanDatabase() {
        sagaRepository.deleteAll();
    }

    @Test
    void shouldAllowOnlyOneSagaForConcurrentBusinessIdentity() {
        sagaRepository.save(saga("tx-1", "key-1"));

        assertThatThrownBy(() -> sagaRepository.save(saga("tx-1", "key-2")))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> sagaRepository.save(saga("tx-2", "key-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectStaleSagaUpdate() {
        PaymentSaga saved = sagaRepository.save(saga("tx-versioned", "key-versioned"));
        PaymentSaga firstCopy = sagaRepository.findById(saved.id()).orElseThrow();
        PaymentSaga staleCopy = sagaRepository.findById(saved.id()).orElseThrow();

        sagaRepository.save(firstCopy.advance(
                PaymentSaga.Status.PROCESSING, PaymentSaga.Step.PAYMENT_CREATED, null));

        assertThatThrownBy(() -> sagaRepository.save(staleCopy.advance(
                PaymentSaga.Status.FAILED, PaymentSaga.Step.FAILED, "stale update")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private PaymentSaga saga(String transactionId, String key) {
        Instant now = Instant.parse("2026-09-30T10:00:00Z");
        return new PaymentSaga(
                null,
                transactionId,
                key,
                new BigDecimal("100.00"),
                PaymentSaga.Status.PROCESSING,
                PaymentSaga.Step.STARTED,
                null,
                "correlation-test",
                now,
                now,
                null
        );
    }
}
