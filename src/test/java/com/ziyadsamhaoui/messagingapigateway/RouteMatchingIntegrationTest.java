package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.List;

import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.cloud.gateway.filter.FilterDefinition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;


class RouteMatchingIntegrationTest extends AbstractGatewayIntegrationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void routeTableContainsExactlyTheDocumentedRoutes() {
        assertThat(routes()).extracting(RouteDefinition::getId)
                .containsExactly("auth-public", "auth-refresh", "auth-authenticated", "user-service",
                        "chat-service-reads", "chat-service-message-send", "chat-service-writes", "realtime-ws",
                        "notification-service");
    }

    @Test
    void realtimeRouteProxiesTheWebSocketPathToTheRealtimeGateway() {
        RouteDefinition realtime = realtimeRoute();

        // http(s), not ws(s): Spring Cloud Gateway upgrades the forwarded URL itself when the inbound
        // request is a WebSocket handshake, while a ws:// URI sends the plain SockJS /info and /xhr
        // requests into the WebSocket handshake handler (rejected -> generic fallback -> 503).
        assertThat(realtime.getUri().getScheme()).isEqualTo("http");
        assertThat(realtime.getUri().toString()).isEqualTo(REALTIME_UPSTREAM.baseUrl());
        assertThat(realtime.getPredicates())
                .singleElement()
                .satisfies(predicate -> assertThat(predicate.getArgs().values()).contains("/ws/**"));
    }

    @Test
    void onlyRequestResponseRoutesCarryTheCircuitBreaker() {
        List<RouteDefinition> allRoutes = routes();

        // The realtime route is a long-lived socket: its handshake commits the response, which the
        // generic forward:/fallback cannot rewrite, so it must not go through the circuit breaker.
        assertThat(filterNames(realtimeRoute())).containsExactly("RequestRateLimiter");

        assertThat(allRoutes)
                .filteredOn(route -> !"realtime-ws".equals(route.getId()))
                .allSatisfy(route -> {
                    assertThat(filterNames(route)).contains("RequestRateLimiter", "CircuitBreaker");
                    FilterDefinition circuitBreaker = route.getFilters().stream()
                            .filter(filter -> "CircuitBreaker".equals(filter.getName()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(circuitBreaker.getArgs()).containsEntry("fallbackUri", "forward:/fallback");
                });
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

    @Test
    void notificationPathsReachTheNotificationService() {
        String token = JwtTestTokens.validToken("route-matching-notifications");

        this.client.get().uri("/notifications").header(AUTHORIZATION, bearer(token)).exchange()
                .expectStatus().isOk();
        this.client.get().uri("/notifications/unread-count").header(AUTHORIZATION, bearer(token)).exchange()
                .expectStatus().isOk();

        assertThat(NOTIFICATION_UPSTREAM.drainRequests()).extracting(RecordedRequest::getPath)
                .containsExactly("/notifications", "/notifications/unread-count");
        assertThat(USER_UPSTREAM.drainRequests()).isEmpty();
        assertThat(CHAT_UPSTREAM.drainRequests()).isEmpty();
        assertThat(AUTH_UPSTREAM.drainRequests()).isEmpty();
    }

    @Test
    void notificationsRequireAuthentication() {
        this.client.get().uri("/notifications/unread-count").exchange().expectStatus().isUnauthorized();
        assertThat(NOTIFICATION_UPSTREAM.drainRequests()).isEmpty();
    }

    private List<RouteDefinition> routes() {
        return this.routeDefinitionLocator.getRouteDefinitions().collectList().block();
    }

    private RouteDefinition realtimeRoute() {
        return routes().stream()
                .filter(route -> "realtime-ws".equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("realtime-ws route is missing"));
    }

    private static List<String> filterNames(RouteDefinition route) {
        return route.getFilters().stream().map(FilterDefinition::getName).toList();
    }
}
