package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;

class InternalPathBlockTest extends AbstractGatewayIntegrationTest {

    private static final String CANONICAL_NOT_FOUND_MESSAGE = "Resource not found";

    @Test
    void internalPathIsBlockedForAnonymousCallers() {
        assertNoRouteMatches(HttpMethod.GET, "/internal/users/1234", null);
    }

    @Test
    void internalPathIsBlockedEvenForAuthenticatedCallers() {
        String token = JwtTestTokens.validToken("internal-probe-user");

        assertNoRouteMatches(HttpMethod.GET, "/internal/users/1234", token);
        assertNoRouteMatches(HttpMethod.PATCH, "/internal/users/1234/last-seen", token);
        assertBlockedByEdgeFilter(HttpMethod.POST, "/rooms/7/internal/messages", token);
    }

    @Test
    void reservedSegmentIsBlockedCaseInsensitivelyAndAsTrailingSegment() {
        String token = JwtTestTokens.validToken("reserved-segment-probe");
        assertBlockedByEdgeFilter(HttpMethod.GET, "/users/internal", token);
        assertBlockedByEdgeFilter(HttpMethod.GET, "/users/Internal/secrets", token);
        assertNoRouteMatches(HttpMethod.GET, "/INTERNAL/users/1234", token);
    }

    private void assertBlockedByEdgeFilter(HttpMethod method, String path, String token) {
        request(method, path, token).expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND")
                .jsonPath("$.message")
                .isEqualTo(CANONICAL_NOT_FOUND_MESSAGE)
                .jsonPath("$.path")
                .isEqualTo(path);

        assertNoUpstreamReceivedAnything();
    }

    private void assertNoRouteMatches(HttpMethod method, String path, String token) {
        request(method, path, token).expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND")
                .jsonPath("$.path")
                .isEqualTo(path);

        assertNoUpstreamReceivedAnything();
    }

    private WebTestClient.ResponseSpec request(HttpMethod method, String path, String token) {
        WebTestClient.RequestBodySpec request = this.client.method(method).uri(path);
        if (token != null) {
            request = request.header(AUTHORIZATION, bearer(token));
        }
        return request.exchange();
    }

    private void assertNoUpstreamReceivedAnything() {
        assertThat(AUTH_UPSTREAM.drainRequests()).isEmpty();
        assertThat(USER_UPSTREAM.drainRequests()).isEmpty();
        assertThat(CHAT_UPSTREAM.drainRequests()).isEmpty();
    }
}
