package com.ziyadsamhaoui.messagingapigateway.filter.rate;

import java.util.Map;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import reactor.core.publisher.Mono;

/**
 * Decorates the Redis rate limiter with a rejection counter.
 *
 * <p>Spring Cloud Gateway applies the limiter inside a filter and only surfaces the outcome as
 * a 429 response. Counting here, on the limiter itself, attributes the rejection to the route
 * that owns it and cannot confuse it with a 429 returned by an upstream service.
 *
 * <p>The delegate stays the only thing that decides; this class only observes the answer.
 */
public class InstrumentedRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    static final String REJECTIONS_METRIC = "badrlink.gateway.ratelimit.rejections";

    private final RedisRateLimiter delegate;
    private final MeterRegistry meterRegistry;

    public InstrumentedRateLimiter(RedisRateLimiter delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate.isAllowed(routeId, id)
                .doOnNext(response -> {
                    if (!response.isAllowed()) {
                        Counter.builder(REJECTIONS_METRIC)
                                .description("Requests rejected by the gateway rate limiter, per route")
                                .tag("route", routeId == null ? "unknown" : routeId)
                                .register(meterRegistry)
                                .increment();
                    }
                });
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return delegate.getConfigClass();
    }

    @Override
    public RedisRateLimiter.Config newConfig() {
        return delegate.newConfig();
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return delegate.getConfig();
    }
}
