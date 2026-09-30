package com.example.paymentservice.controller;

import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldCreatePaymentAndReturnHttp201() throws Exception {
        Payment payment = payment("transaction-123");
        when(paymentService.create(any())).thenReturn(payment);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "correlation-test-123")
                        .content("""
                                {
                                  "transactionId": "transaction-123",
                                  "amount": 150.00,
                                  "idempotencyKey": "key-123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "correlation-test-123"))
                .andExpect(jsonPath("$.transactionId").value("transaction-123"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.status").value("CREATED"));

        verify(paymentService).create(any());
    }

    @Test
    void shouldRejectInvalidRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId": "transaction-123",
                                  "amount": 0,
                                  "idempotencyKey": "key-123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("amount: amount must be greater than zero"));

        verify(paymentService, never()).create(any());
    }

    @Test
    void shouldReturnHttp404WhenPaymentDoesNotExist() throws Exception {
        when(paymentService.findByTransactionId("missing-transaction"))
                .thenThrow(new PaymentNotFoundException("missing-transaction"));

        mockMvc.perform(get("/payments/missing-transaction"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Payment not found for transactionId: missing-transaction"));
    }

    private Payment payment(String transactionId) {
        return new Payment(
                "payment-123",
                transactionId,
                new BigDecimal("150.00"),
                PaymentStatus.CREATED,
                "key-123",
                Instant.parse("2026-09-30T10:00:00Z")
        );
    }
}
