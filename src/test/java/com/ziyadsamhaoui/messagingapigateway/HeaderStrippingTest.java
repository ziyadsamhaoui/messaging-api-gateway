package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.Duration;

import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ziyadsamhaoui.messagingapigateway.filter.global.GlobalLoggingFilter;
import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;

/**
 * A client must never be able to hand a downstream service the service-to-service credential
 * ({@code X-Internal-Token}) — the gateway strips it from every inbound request (ADR-008).
 */
class HeaderStrippingTest extends AbstractGatewayIntegrationTest {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Test
    void clientSuppliedInternalTokenIsStrippedOnPublicRoutes() {
        this.client.post()
                .uri("/auth/refresh")
                .header(INTERNAL_TOKEN_HEADER, "forged-service-credential")
                .exchange()
                .expectStatus()
                .isOk();

        RecordedRequest forwarded = AUTH_UPSTREAM.awaitRequest(Duration.ofSeconds(5));
        assertThat(forwarded.getPath()).isEqualTo("/auth/refresh");
        assertThat(forwarded.getHeader(INTERNAL_TOKEN_HEADER)).isNull();
    }

    @Test
    void clientSuppliedInternalTokenIsStrippedOnAuthenticatedRoutes() {
        String token = JwtTestTokens.validToken("header-stripping-user");

        this.client.get()
                .uri("/users/me")
                .header(AUTHORIZATION, bearer(token))
                .header(INTERNAL_TOKEN_HEADER, "forged-service-credential")
                .exchange()
                .expectStatus()
                .isOk();

        RecordedRequest forwarded = USER_UPSTREAM.awaitRequest(Duration.ofSeconds(5));
        assertThat(forwarded.getPath()).isEqualTo("/users/me");
        // The credential is gone, and the gateway does not substitute one of its own either.
        assertThat(forwarded.getHeader(INTERNAL_TOKEN_HEADER)).isNull();
        assertThat(forwarded.getHeaders().toMultimap()).doesNotContainKey(INTERNAL_TOKEN_HEADER.toLowerCase());
    }

    @Test
    void correlationIdIsPropagatedToTheUpstreamAndBackToTheClient() {
        String token = JwtTestTokens.validToken("correlation-user");
        String correlationId = "it-correlation-1234";

        this.client.get()
                .uri("/users/me")
                .header(AUTHORIZATION, bearer(token))
                .header(GlobalLoggingFilter.CORRELATION_HEADER, correlationId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(GlobalLoggingFilter.CORRELATION_HEADER, correlationId);

        RecordedRequest forwarded = USER_UPSTREAM.awaitRequest(Duration.ofSeconds(5));
        assertThat(forwarded.getHeader(GlobalLoggingFilter.CORRELATION_HEADER)).isEqualTo(correlationId);
        assertThat(forwarded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo(bearer(token));
    }
}
