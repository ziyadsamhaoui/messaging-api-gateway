package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;

/**
 * {@code /internal/**} must be unreachable from the public edge, with or without a valid token, and no
 * request may ever leave the gateway (ADR-008).
 *
 * <p>Two cases are covered, because two different components guarantee the same 404 contract:
 * <ul>
 * <li>a path that <em>would</em> match a route (for example {@code /rooms/7/internal/messages}) is
 * terminated by {@code InternalPathBlockFilter} with the canonical body;</li>
 * <li>a path that matches no route at all is answered by the gateway's error handling — also 404 with
 * the canonical body, so the two cases are indistinguishable to a caller.</li>
 * </ul>
 */
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
        // These match the authenticated user-service route, so a token is required; once authenticated
        // the reserved segment is what stops the request.
        String token = JwtTestTokens.validToken("reserved-segment-probe");
        assertBlockedByEdgeFilter(HttpMethod.GET, "/users/internal", token);
        assertBlockedByEdgeFilter(HttpMethod.GET, "/users/Internal/secrets", token);
        // No route matches a differently-cased segment, so the caller only ever sees an opaque 404.
        assertNoRouteMatches(HttpMethod.GET, "/INTERNAL/users/1234", token);
    }

    /** A path that would otherwise be proxied to a backend: the edge block filter must win. */
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

    /** A path no route matches: it must be just as opaque, and must not reach a backend either. */
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
