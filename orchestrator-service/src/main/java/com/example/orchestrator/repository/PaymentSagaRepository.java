package com.example.orchestrator.repository;

import com.example.orchestrator.model.PaymentSaga;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface PaymentSagaRepository extends MongoRepository<PaymentSaga, String> {
    Optional<PaymentSaga> findByTransactionId(String transactionId);
    Optional<PaymentSaga> findByIdempotencyKey(String idempotencyKey);
}
