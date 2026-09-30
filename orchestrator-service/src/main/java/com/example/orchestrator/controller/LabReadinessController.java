package com.example.orchestrator.controller;

import com.example.orchestrator.client.PaymentClient;
import com.example.orchestrator.client.SplitClient;
import com.example.orchestrator.client.TransferClient;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class LabReadinessController {

    private final HealthEndpoint healthEndpoint;
    private final PaymentClient paymentClient;
    private final SplitClient splitClient;
    private final TransferClient transferClient;

    public LabReadinessController(HealthEndpoint healthEndpoint,
                                  PaymentClient paymentClient,
                                  SplitClient splitClient,
                                  TransferClient transferClient) {
        this.healthEndpoint = healthEndpoint;
        this.paymentClient = paymentClient;
        this.splitClient = splitClient;
        this.transferClient = transferClient;
    }

    @GetMapping("/api/lab/readiness")
    public ResponseEntity<ReadinessSnapshot> readiness() {
        Map<String, String> services = new LinkedHashMap<>();
        services.put("orchestrator", probeOrchestrator());
        services.put("payment", probe(paymentClient::ping));
        services.put("split", probe(splitClient::ping));
        services.put("transfer", probe(transferClient::ping));

        boolean ready = services.values().stream().allMatch("UP"::equals);
        ReadinessSnapshot snapshot = new ReadinessSnapshot(ready, services, Instant.now());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(snapshot);
    }

    private String probeOrchestrator() {
        try {
            return Status.UP.equals(healthEndpoint.health().getStatus()) ? "UP" : "STARTING";
        } catch (RuntimeException exception) {
            return "STARTING";
        }
    }

    private String probe(Runnable probe) {
        try {
            probe.run();
            return "UP";
        } catch (RuntimeException exception) {
            return "STARTING";
        }
    }

    public record ReadinessSnapshot(boolean ready, Map<String, String> services, Instant checkedAt) {
    }
}
