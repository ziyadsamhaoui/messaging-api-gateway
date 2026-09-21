package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.List;

import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;

/**
 * Verifies that every path in the documented routing matrix reaches the intended upstream, and that
 * the two WebSocket/realtime contracts are expressed in the route table.
 */
class RouteMatchingIntegrationTest extends AbstractGatewayIntegrationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void routeTableContainsExactlyTheDocumentedRoutes() {
        assertThat(routes()).extracting(RouteDefinition::getId)
                .containsExactly("auth-public", "auth-refresh", "auth-authenticated", "user-service",
                        "chat-service-reads", "chat-service-message-send", "chat-service-writes", "realtime-ws");
    }

    @Test
    void realtimeRouteProxiesTheWebSocketPathToTheRealtimeGateway() {
        RouteDefinition realtime = routes().stream()
                .filter(route -> "realtime-ws".equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("realtime-ws route is missing"));

        assertThat(realtime.getUri().getScheme()).isEqualTo("ws");
        assertThat(realtime.getUri().getPort()).isEqualTo(8084);
        assertThat(realtime.getPredicates())
                .singleElement()
                .satisfies(predicate -> assertThat(predicate.getArgs().values()).contains("/ws/**"));
    }

    @Test
    void publicAuthPathsAreProxiedToTheAuthService() {
        this.client.post()
                .uri("/auth/login")
                .exchange()
                .expectStatus()
                .isOk();

        List<RecordedRequest> received = AUTH_UPSTREAM.drainRequests();
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().getMethod()).isEqualTo("POST");
        assertThat(received.getFirst().getPath()).isEqualTo("/auth/login");
        assertThat(USER_UPSTREAM.drainRequests()).isEmpty();
        assertThat(CHAT_UPSTREAM.drainRequests()).isEmpty();
    }

    @Test
    void userAndChatPathsReachTheirOwnUpstreams() {
        String token = JwtTestTokens.validToken("route-matching-user");

        this.client.get().uri("/users/me").header(AUTHORIZATION, bearer(token)).exchange().expectStatus().isOk();
        this.client.get().uri("/rooms/42").header(AUTHORIZATION, bearer(token)).exchange().expectStatus().isOk();
        this.client.post()
                .uri("/rooms/42/messages")
                .header(AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(USER_UPSTREAM.drainRequests()).extracting(RecordedRequest::getPath).containsExactly("/users/me");
        assertThat(CHAT_UPSTREAM.drainRequests()).extracting(RecordedRequest::getPath)
                .containsExactly("/rooms/42", "/rooms/42/messages");
        assertThat(AUTH_UPSTREAM.drainRequests()).isEmpty();
    }

    private List<RouteDefinition> routes() {
        return this.routeDefinitionLocator.getRouteDefinitions().collectList().block();
    }
}
