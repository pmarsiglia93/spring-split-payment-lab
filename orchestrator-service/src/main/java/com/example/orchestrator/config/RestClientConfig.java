package com.example.orchestrator.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.io.IOException;
import java.time.Duration;

@Configuration
public class RestClientConfig {
    @Bean("paymentRestClient")
    RestClient paymentRestClient(@Value("${services.payment.url}") String url,
                                 @Value("${services.auth.enabled}") boolean serviceAuthEnabled) throws IOException {
        return client(url, 2000, serviceAuthEnabled);
    }

    @Bean("splitRestClient")
    RestClient splitRestClient(@Value("${services.split.url}") String url,
                               @Value("${services.auth.enabled}") boolean serviceAuthEnabled) throws IOException {
        return client(url, 1000, serviceAuthEnabled);
    }

    @Bean("transferRestClient")
    RestClient transferRestClient(@Value("${services.transfer.url}") String url,
                                  @Value("${services.auth.enabled}") boolean serviceAuthEnabled) throws IOException {
        return client(url, 2000, serviceAuthEnabled);
    }

    private RestClient client(String baseUrl, int readTimeoutMillis, boolean serviceAuthEnabled) throws IOException {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(new CloudRunIdTokenInterceptor(baseUrl, serviceAuthEnabled))
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
