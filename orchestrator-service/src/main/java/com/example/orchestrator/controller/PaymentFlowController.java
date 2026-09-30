package com.example.orchestrator.controller;

import com.example.orchestrator.model.PaymentSaga;
import com.example.orchestrator.service.PaymentFlowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payment-flows")
public class PaymentFlowController {
    private final PaymentFlowService paymentFlowService;

    public PaymentFlowController(PaymentFlowService paymentFlowService) {
        this.paymentFlowService = paymentFlowService;
    }

    @PostMapping
    public ResponseEntity<PaymentSaga> start(@Valid @RequestBody CreateFlowRequest request) {
        PaymentSaga saga = paymentFlowService.start(request.transactionId(), request.amount(),
                request.idempotencyKey(), request.simulation());
        return ResponseEntity.status(HttpStatus.CREATED).body(saga);
    }

    @GetMapping("/{transactionId}")
    public PaymentSaga find(@PathVariable String transactionId) {
        return paymentFlowService.find(transactionId);
    }

    public record CreateFlowRequest(
            @NotBlank String transactionId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
            @NotBlank String idempotencyKey,
            String simulation
    ) {
    }
}
