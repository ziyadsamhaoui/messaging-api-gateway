package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;

/**
 * SockJS/WebSocket edge behaviour for the {@code realtime-ws} route.
 *
 * <p>Browsers cannot set an {@code Authorization} header on the SockJS transport requests, so the
 * gateway accepts {@code ?access_token=} on {@code /ws/**} only. The handshake requests are plain
 * HTTP (except the final WebSocket upgrade), which means the route must proxy them like any other
 * HTTP call instead of routing them through the request/response circuit-breaker fallback.
 */
class RealtimeProxyIntegrationTest extends AbstractGatewayIntegrationTest {

    @Test
    void sockJsInfoIsProxiedToTheRealtimeGatewayWhenTheQueryTokenIsValid() {
        String token = JwtTestTokens.validToken("sockjs-info-user");

        this.client.get()
                .uri(uriBuilder -> uriBuilder.path("/ws/info").queryParam("access_token", token).build())
                .exchange()
                .expectStatus()
                .isOk();

        RecordedRequest forwarded = REALTIME_UPSTREAM.awaitRequest(Duration.ofSeconds(5));
        assertThat(forwarded.getMethod()).isEqualTo("GET");
        assertThat(forwarded.getPath()).startsWith("/ws/info");
    }

    @Test
    void sockJsInfoWithoutATokenIsUnauthenticatedAndNeverReachesTheRealtimeGateway() {
        this.client.get()
                .uri("/ws/info")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.path")
                .isEqualTo("/ws/info");

        assertThat(REALTIME_UPSTREAM.drainRequests()).isEmpty();
    }

    @Test
    void sockJsTransportRequestIsProxiedWithTheQueryToken() {
        String token = JwtTestTokens.validToken("sockjs-transport-user");

        this.client.get()
                .uri(uriBuilder -> uriBuilder.path("/ws/000/session123/xhr")
                        .queryParam("access_token", token)
                        .build())
                .exchange()
                .expectStatus()
                .isOk();

        RecordedRequest forwarded = REALTIME_UPSTREAM.awaitRequest(Duration.ofSeconds(5));
        assertThat(forwarded.getPath()).startsWith("/ws/000/session123/xhr");
    }

    @Test
    void realtimeRouteForwardsTheUpstreamStatusInsteadOfRewritingItThroughTheFallback() {
        // The realtime upstream answers 503. Because the route carries no CircuitBreaker/fallback, the
        // gateway forwards that response verbatim; a generic fallback would have rewritten the body to
        // UPSTREAM_UNAVAILABLE with the caller's path, masking the realtime service's own contract.
        String token = JwtTestTokens.validToken("realtime-failure-user");
        REALTIME_UPSTREAM.respondWithJson(503, "{\"code\":\"REALTIME_DOWN\"}");

        this.client.get()
                .uri(uriBuilder -> uriBuilder.path("/ws/info").queryParam("access_token", token).build())
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("REALTIME_DOWN");
    }
}
