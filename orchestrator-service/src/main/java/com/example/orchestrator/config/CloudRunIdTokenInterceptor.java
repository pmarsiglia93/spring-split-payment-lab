package com.example.orchestrator.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Adds a short-lived Google-signed ID token when the destination is a private
 * Cloud Run service. Local Docker execution keeps this interceptor disabled.
 */
final class CloudRunIdTokenInterceptor implements ClientHttpRequestInterceptor {

    private final IdTokenCredentials credentials;

    CloudRunIdTokenInterceptor(String audience, boolean enabled) throws IOException {
        if (!enabled) {
            this.credentials = null;
            return;
        }

        GoogleCredentials applicationCredentials = GoogleCredentials.getApplicationDefault();
        if (!(applicationCredentials instanceof IdTokenProvider idTokenProvider)) {
            throw new IllegalStateException("Application Default Credentials cannot issue Cloud Run ID tokens");
        }

        this.credentials = IdTokenCredentials.newBuilder()
                .setIdTokenProvider(idTokenProvider)
                .setTargetAudience(audience)
                .build();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (credentials != null) {
            AccessToken token;
            synchronized (credentials) {
                credentials.refreshIfExpired();
                token = credentials.getAccessToken();
                if (token == null) {
                    token = credentials.refreshAccessToken();
                }
            }
            request.getHeaders().setBearerAuth(token.getTokenValue());
        }
        return execution.execute(request, body);
    }
}
