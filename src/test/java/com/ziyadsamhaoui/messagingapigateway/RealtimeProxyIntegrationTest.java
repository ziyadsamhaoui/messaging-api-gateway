package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;

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
