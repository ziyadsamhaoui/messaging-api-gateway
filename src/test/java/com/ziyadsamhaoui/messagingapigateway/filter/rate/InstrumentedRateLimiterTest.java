package com.ziyadsamhaoui.messagingapigateway.filter.rate;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentedRateLimiterTest {

    private RedisRateLimiter delegate;
    private SimpleMeterRegistry registry;
    private InstrumentedRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        delegate = mock(RedisRateLimiter.class);
        registry = new SimpleMeterRegistry();
        rateLimiter = new InstrumentedRateLimiter(delegate, registry);
    }

    @Test
    void allowedRequestsDoNotIncrementTheRejectionCounter() {
        when(delegate.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(true, Map.of())));

        StepVerifier.create(rateLimiter.isAllowed("chat", "user-1"))
                .expectNextMatches(RateLimiter.Response::isAllowed)
                .verifyComplete();

        assertThat(registry.find(InstrumentedRateLimiter.REJECTIONS_METRIC).counter()).isNull();
    }

    @Test
    void rejectionsAreCountedPerRoute() {
        when(delegate.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(false, Map.of())));

        StepVerifier.create(rateLimiter.isAllowed("auth-public", "ip-1")).expectNextCount(1).verifyComplete();
        StepVerifier.create(rateLimiter.isAllowed("auth-public", "ip-2")).expectNextCount(1).verifyComplete();
        StepVerifier.create(rateLimiter.isAllowed("chat", "ip-1")).expectNextCount(1).verifyComplete();

        assertThat(registry.get(InstrumentedRateLimiter.REJECTIONS_METRIC).tags("route", "auth-public")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get(InstrumentedRateLimiter.REJECTIONS_METRIC).tags("route", "chat")
                .counter().count()).isEqualTo(1.0);
    }
}
