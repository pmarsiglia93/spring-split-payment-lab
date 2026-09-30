package com.example.splitservice.controller;

import com.example.splitservice.service.SplitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/splits")
public class SplitController {
    private final SplitService splitService;

    public SplitController(SplitService splitService) {
        this.splitService = splitService;
    }

    @PostMapping
    public SplitService.SplitResult calculate(@Valid @RequestBody SplitRequest request) {
        return splitService.calculate(request.transactionId(), request.amount(), request.simulation());
    }

    public record SplitRequest(
            @NotBlank String transactionId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
            String simulation
    ) {
    }
}
