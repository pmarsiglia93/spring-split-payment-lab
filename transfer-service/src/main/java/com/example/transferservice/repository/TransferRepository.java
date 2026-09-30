package com.example.transferservice.repository;

import com.example.transferservice.model.TransferBatch;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface TransferRepository extends MongoRepository<TransferBatch, String> {
    Optional<TransferBatch> findByTransactionId(String transactionId);
}
