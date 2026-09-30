package com.example.orchestrator.controller;

import com.example.orchestrator.client.PaymentClient;
import com.example.orchestrator.client.SplitClient;
import com.example.orchestrator.client.TransferClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabReadinessControllerTest {

    private final HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
    private final PaymentClient paymentClient = mock(PaymentClient.class);
    private final SplitClient splitClient = mock(SplitClient.class);
    private final TransferClient transferClient = mock(TransferClient.class);
    private final LabReadinessController controller = new LabReadinessController(
            healthEndpoint, paymentClient, splitClient, transferClient);

    @Test
    void reportsReadyWhenEveryServiceIsAvailable() {
        when(healthEndpoint.health()).thenReturn(Health.up().build());

        LabReadinessController.ReadinessSnapshot snapshot = controller.readiness().getBody();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.services()).containsOnlyKeys("orchestrator", "payment", "split", "transfer");
        assertThat(snapshot.services().values()).containsOnly("UP");
    }

    @Test
    void reportsStartingWhileAnInternalServiceWakesUp() {
        when(healthEndpoint.health()).thenReturn(Health.up().build());
        doThrow(new IllegalStateException("cold start")).when(paymentClient).ping();

        LabReadinessController.ReadinessSnapshot snapshot = controller.readiness().getBody();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.services().get("payment")).isEqualTo("STARTING");
    }
}
