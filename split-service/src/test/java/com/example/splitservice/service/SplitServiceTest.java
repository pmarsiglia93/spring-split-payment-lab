package com.example.splitservice.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SplitServiceTest {
    private final SplitService splitService = new SplitService();

    @Test
    void shouldSplitNinetyPercentToSellerAndTenPercentToPlatform() {
        SplitService.SplitResult result = splitService.calculate(
                "transaction-1", new BigDecimal("100.00"), "NONE"
        );

        assertEquals(new BigDecimal("90.00"), result.sellerAmount());
        assertEquals(new BigDecimal("10.00"), result.platformFee());
    }
}
