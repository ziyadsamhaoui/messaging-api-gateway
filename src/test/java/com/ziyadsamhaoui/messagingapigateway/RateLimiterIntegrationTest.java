package com.ziyadsamhaoui.messagingapigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import com.ziyadsamhaoui.messagingapigateway.support.AbstractGatewayIntegrationTest;

/**
 * Token bucket behaviour against a real Redis container.
 *
 * <p>The {@code auth-public} route is configured with 1 token/second and a burst capacity of 5, which
 * makes the throttle visible in a handful of requests. The bucket key includes the route id, so the
 * separate {@code auth-refresh} bucket must still serve traffic while {@code /auth/login} is throttled.
 */
class RateLimiterIntegrationTest extends AbstractGatewayIntegrationTest {

    private static final String IP_KEYED_PATH = "/auth/login";

    private static final String OTHER_IP_KEYED_PATH = "/auth/refresh";

    private static final int BURST_CAPACITY = 5;

    /** Time for an exhausted bucket to refill completely at 1 token/second. */
    private static final Duration BUCKET_REFILL = Duration.ofSeconds(BURST_CAPACITY + 1);

    /** One token is enough to be served again. */
    private static final Duration SINGLE_TOKEN_REFILL = Duration.ofSeconds(2);

    @Test
    void requestsBeyondTheBurstCapacityGet429AndRecoverAfterTheRefillPeriod() throws InterruptedException {
        // Start from a full bucket so the outcome does not depend on the tests that ran before.
        Thread.sleep(BUCKET_REFILL.toMillis());

        List<EntityExchangeResult<byte[]>> responses = new ArrayList<>();
        for (int i = 0; i < BURST_CAPACITY + 3; i++) {
            responses.add(postTo(IP_KEYED_PATH));
        }

        List<Integer> statuses = responses.stream().map(response -> response.getStatus().value()).toList();
        assertThat(statuses).as("burst of %d requests at 1 token/second", BURST_CAPACITY + 3)
                .contains(200)
                .contains(429);

        EntityExchangeResult<byte[]> throttled = responses.stream()
                .filter(response -> response.getStatus().value() == 429)
                .findFirst()
                .orElseThrow();
        assertThat(throttled.getResponseHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");

        EntityExchangeResult<byte[]> allowed = responses.stream()
                .filter(response -> response.getStatus().value() == 200)
                .findFirst()
                .orElseThrow();
        assertThat(allowed.getResponseHeaders().getFirst("X-RateLimit-Burst-Capacity"))
                .isEqualTo(String.valueOf(BURST_CAPACITY));
        assertThat(allowed.getResponseHeaders().getFirst("X-RateLimit-Replenish-Rate")).isEqualTo("1");

        // A different route keeps its own bucket: the throttle is per route and per client, not global.
        assertThat(postTo(OTHER_IP_KEYED_PATH).getStatus().value()).isEqualTo(200);

        Thread.sleep(SINGLE_TOKEN_REFILL.toMillis());
        assertThat(postTo(IP_KEYED_PATH).getStatus().value()).isEqualTo(200);
    }

    private EntityExchangeResult<byte[]> postTo(String path) {
        return this.client.post().uri(path).exchange().expectBody().returnResult();
    }
}
