package com.ziyadsamhaoui.messagingapigateway.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import reactor.test.StepVerifier;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit coverage for the failure paths that are hard to provoke over HTTP (a dead upstream, a Redis
 * outage) and for the promise that internal details never leave the edge.
 */
class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler(
            new GatewayErrorWriter(JsonMapper.builder().build()));

    @Test
    void upstreamConnectionFailureBecomesUpstreamUnavailable() {
        MockServerWebExchange exchange = handle("/users/me", new ConnectException("Connection refused"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        String body = body(exchange);
        assertThat(body).contains("\"code\":\"UPSTREAM_UNAVAILABLE\"")
                .contains("\"status\":503")
                .contains("\"path\":\"/users/me\"")
                .contains("\"message\":\"Upstream service is unavailable\"")
                // The transport detail is logged, never returned to the client.
                .doesNotContain("Connection refused");
    }

    @Test
    void upstreamTimeoutBecomesUpstreamUnavailable() {
        MockServerWebExchange exchange = handle("/rooms/7/messages", new TimeoutException("3s exceeded"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(exchange)).contains("\"code\":\"UPSTREAM_UNAVAILABLE\"");
    }

    @Test
    void redisOutageBecomesRateLimiterUnavailable() {
        MockServerWebExchange exchange = handle("/auth/login",
                new RedisConnectionFailureException("Redis is not reachable"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(exchange)).contains("\"code\":\"RATE_LIMITER_UNAVAILABLE\"");
    }

    @Test
    void responseStatusExceptionsKeepTheirStatusAndReason() {
        MockServerWebExchange exchange = handle("/nope",
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching handler"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(exchange)).contains("\"code\":\"NOT_FOUND\"").contains("No matching handler");
    }

    @Test
    void unexpectedFailuresBecomeA500WithoutLeakingDetails() {
        MockServerWebExchange exchange = handle("/rooms/7/messages",
                new IllegalStateException("database password is hunter2"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(exchange)).contains("\"code\":\"INTERNAL_ERROR\"")
                .doesNotContain("hunter2")
                .doesNotContain("IllegalStateException");
    }

    private MockServerWebExchange handle(String path, Throwable throwable) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        StepVerifier.create(this.handler.handle(exchange, throwable)).verifyComplete();
        return exchange;
    }

    private String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
