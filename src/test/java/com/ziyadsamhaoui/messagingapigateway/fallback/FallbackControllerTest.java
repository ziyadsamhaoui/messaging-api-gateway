package com.ziyadsamhaoui.messagingapigateway.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.ziyadsamhaoui.messagingapigateway.dto.ErrorResponse;

/**
 * When a circuit is open (or an upstream times out) the client must receive a truthful 503 carrying the
 * path it actually asked for — not the internal {@code /fallback} forward.
 */
class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void fallbackAnswers503WithTheOriginalRequestPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/rooms/42/messages").build());
        ServerWebExchangeUtils.addOriginalRequestUrl(exchange, URI.create("http://localhost:8080/rooms/42/messages"));

        ResponseEntity<ErrorResponse> response = this.controller.fallback(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(503);
        assertThat(body.code()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(body.path()).isEqualTo("/rooms/42/messages");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void fallbackFallsBackToTheForwardedPathWhenNoOriginalUrlIsAvailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/fallback").build());

        ResponseEntity<ErrorResponse> response = this.controller.fallback(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/fallback");
    }
}
