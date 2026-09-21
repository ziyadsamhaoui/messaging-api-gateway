package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.Duration;

import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;
import com.ziyadsamhaoui.messagingapigateway.support.JwtTestTokens;

/**
 * Edge token verification: only a correctly signed, unexpired token from the trusted issuer may reach a
 * backend, and the token is forwarded untouched — the gateway injects no identity headers of its own.
 */
class JwtEdgeAuthFilterTest extends AbstractGatewayIntegrationTest {

    private static final String PROTECTED_PATH = "/users/me";

    /** Public, authenticated routes share a rate-limit bucket, so use a distinct one here. */
    private static final String PUBLIC_PATH = "/auth/refresh";

    @Test
    void validTokenIsForwardedUntouchedToTheUpstream() {
        String token = JwtTestTokens.validToken("edge-auth-user");

        this.client.get()
                .uri(PROTECTED_PATH)
                .header(AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus()
                .isOk();

        RecordedRequest forwarded = USER_UPSTREAM.awaitRequest(Duration.ofSeconds(5));
        assertThat(forwarded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo(bearer(token));
        // No downstream identity headers: every service derives the subject from the token itself.
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(forwarded.getHeader("X-User-Sub")).isNull();
        assertThat(forwarded.getHeader("X-Authenticated-User")).isNull();
    }

    @Test
    void expiredTokenIsRejectedWithTheCanonicalUnauthenticatedBody() {
        assertRejected(JwtTestTokens.expiredToken("expired-user"));
    }

    @Test
    void malformedTokenIsRejected() {
        assertRejected(JwtTestTokens.malformedToken());
    }

    @Test
    void tokenSignedByAPrivateKeyIsRejected() {
        assertRejected(JwtTestTokens.tokenSignedByUnknownKey("forged-user"));
    }

    @Test
    void tokenFromAnUntrustedIssuerIsRejected() {
        assertRejected(JwtTestTokens.tokenWithWrongIssuer("untrusted-issuer-user"));
    }

    @Test
    void protectedRouteWithoutTokenIsUnauthenticatedAndNeverReachesABackend() {
        this.client.get()
                .uri(PROTECTED_PATH)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401)
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.path")
                .isEqualTo(PROTECTED_PATH);

        assertThat(USER_UPSTREAM.drainRequests()).isEmpty();
    }

    @Test
    void publicRouteIsServedWithoutAToken() {
        this.client.post()
                .uri(PUBLIC_PATH)
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(AUTH_UPSTREAM.drainRequests()).hasSize(1);
    }

    @Test
    void queryParameterTokenIsNotAcceptedOutsideTheWebSocketHandshake() {
        String token = JwtTestTokens.validToken("query-parameter-user");

        this.client.get()
                .uri(uriBuilder -> uriBuilder.path(PROTECTED_PATH).queryParam("access_token", token).build())
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");

        assertThat(USER_UPSTREAM.drainRequests()).isEmpty();
    }

    @Test
    void webSocketHandshakeRequiresAToken() {
        this.client.get()
                .uri("/ws/chat")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    private void assertRejected(String token) {
        this.client.get()
                .uri(PROTECTED_PATH)
                .header(AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401)
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.message")
                .isEqualTo("Invalid or expired JWT token")
                .jsonPath("$.path")
                .isEqualTo(PROTECTED_PATH);

        assertThat(USER_UPSTREAM.drainRequests()).isEmpty();
    }
}
