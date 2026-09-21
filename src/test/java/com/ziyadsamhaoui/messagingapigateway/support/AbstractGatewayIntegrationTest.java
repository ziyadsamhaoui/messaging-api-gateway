package com.ziyadsamhaoui.messagingapigateway.support;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.ziyadsamhaoui.messagingapigateway.MessagingApiGatewayApplication;

/**
 * Boots the real gateway on a random port in front of stub upstreams and a real Redis container, so
 * integration tests exercise the actual reactive pipeline (routing, security filters, rate limiter)
 * over HTTP instead of mocking it.
 *
 * <p>Stub upstreams and the Redis container are singletons shared by the whole JVM, which keeps the
 * Spring context cacheable across test classes.
 */
@SpringBootTest(classes = MessagingApiGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractGatewayIntegrationTest {

    protected static final UpstreamStub AUTH_UPSTREAM = UpstreamStub.start("auth-service");

    protected static final UpstreamStub USER_UPSTREAM = UpstreamStub.start("user-service");

    protected static final UpstreamStub CHAT_UPSTREAM = UpstreamStub.start("chat-service");

    static {
        // The Auth Service publishes the JWKS document the gateway verifies signatures against.
        AUTH_UPSTREAM.onRoute(JwtTestTokens.JWKS_PATH,
                request -> UpstreamStub.jsonResponse(200, JwtTestTokens.jwksJson()));
    }

    @LocalServerPort
    private int port;

    protected WebTestClient client;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
        registry.add("UPSTREAM_AUTH_SERVICE", AUTH_UPSTREAM::baseUrl);
        registry.add("UPSTREAM_USER_SERVICE", USER_UPSTREAM::baseUrl);
        registry.add("UPSTREAM_CHAT_SERVICE", CHAT_UPSTREAM::baseUrl);
        registry.add("AUTH_JWKS_URI", () -> AUTH_UPSTREAM.url(JwtTestTokens.JWKS_PATH));
        registry.add("AUTH_ISSUER", () -> JwtTestTokens.ISSUER);
    }

    @BeforeEach
    void resetGatewayTestState() {
        this.client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + this.port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
        AUTH_UPSTREAM.reset();
        USER_UPSTREAM.reset();
        CHAT_UPSTREAM.reset();
        AUTH_UPSTREAM.respondOk();
        USER_UPSTREAM.respondOk();
        CHAT_UPSTREAM.respondOk();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
