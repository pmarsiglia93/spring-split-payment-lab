package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
class PaymentRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindPaymentByTransactionIdInRealMongoDb() {
        paymentRepository.save(payment("transaction-integration-1", "key-integration-1"));

        var result = paymentRepository.findByTransactionId("transaction-integration-1");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getAmount()).isEqualByComparingTo("250.00");
        assertThat(result.orElseThrow().getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void shouldEnforceUniqueTransactionIdAtDatabaseLevel() {
        paymentRepository.save(payment("transaction-unique", "key-1"));

        assertThatThrownBy(() -> paymentRepository.save(payment("transaction-unique", "key-2")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldEnforceUniqueIdempotencyKeyAtDatabaseLevel() {
        paymentRepository.save(payment("transaction-1", "same-key"));

        assertThatThrownBy(() -> paymentRepository.save(payment("transaction-2", "same-key")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectStaleConcurrentUpdate() {
        Payment saved = paymentRepository.save(payment("transaction-versioned", "key-versioned"));
        Payment firstCopy = paymentRepository.findById(saved.getId()).orElseThrow();
        Payment staleCopy = paymentRepository.findById(saved.getId()).orElseThrow();

        firstCopy.updateStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(firstCopy);
        staleCopy.updateStatus(PaymentStatus.COMPLETED);

        assertThatThrownBy(() -> paymentRepository.save(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private Payment payment(String transactionId, String idempotencyKey) {
        return new Payment(
                null,
                transactionId,
                new BigDecimal("250.00"),
                PaymentStatus.CREATED,
                idempotencyKey,
                Instant.parse("2026-09-30T10:00:00Z")
        );
    }
}
