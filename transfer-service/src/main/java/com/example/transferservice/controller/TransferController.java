package com.example.transferservice.controller;

import com.example.transferservice.model.TransferBatch;
import com.example.transferservice.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public TransferBatch create(@Valid @RequestBody TransferRequest request) {
        return transferService.create(request.transactionId(), request.sellerAmount(),
                request.platformFee(), request.idempotencyKey(), request.simulation());
    }

    @PostMapping("/{transactionId}/compensate")
    public TransferBatch compensate(@PathVariable String transactionId) {
        return transferService.compensate(transactionId);
    }

    @GetMapping("/{transactionId}")
    public TransferBatch find(@PathVariable String transactionId) {
        return transferService.find(transactionId);
    }

    public record TransferRequest(
            @NotBlank String transactionId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal sellerAmount,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal platformFee,
            @NotBlank String idempotencyKey,
            String simulation
    ) {
    }
}
