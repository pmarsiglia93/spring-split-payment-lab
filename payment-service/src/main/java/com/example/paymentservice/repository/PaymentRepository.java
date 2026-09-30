package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByTransactionId(String transactionId);
}
