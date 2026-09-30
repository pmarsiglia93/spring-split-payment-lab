package com.example.splitservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class SplitService {
    private static final Logger log = LoggerFactory.getLogger(SplitService.class);
    private static final BigDecimal SELLER_RATE = new BigDecimal("0.90");

    public SplitResult calculate(String transactionId, BigDecimal amount, String simulation) {
        log.info("Calculating split transactionId={} simulation={}", transactionId, simulation);
        if ("SPLIT_TIMEOUT".equals(simulation)) {
            sleep(2500);
        }
        if ("SPLIT_FAILURE".equals(simulation)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Simulated split failure");
        }
        BigDecimal sellerAmount = amount.multiply(SELLER_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformFee = amount.subtract(sellerAmount).setScale(2, RoundingMode.HALF_UP);
        return new SplitResult(transactionId, sellerAmount, platformFee);
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Split interrupted", exception);
        }
    }

    public record SplitResult(String transactionId, BigDecimal sellerAmount, BigDecimal platformFee) {
    }
}
