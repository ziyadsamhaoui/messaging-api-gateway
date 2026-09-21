package com.ziyadsamhaoui.messagingapigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Token-bucket rate limiting backed by Redis ({@code RedisRateLimiter} executes a Lua script that
 * refills the bucket atomically, so the decision is consistent across gateway replicas).
 *
 * <p>This bean supplies the fallback bucket. Every route in the YAML route table overrides the limits
 * per route; the effective matrix is documented in {@code /docs/API_ENDPOINTS.md}.
 *
 * <p>{@code X-RateLimit-Remaining}, {@code X-RateLimit-Burst-Capacity} and
 * {@code X-RateLimit-Replenish-Rate} response headers are always included so clients can back off
 * before they are throttled.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${badrlink.gateway.rate-limit.default-replenish-rate:10}") int defaultReplenishRate,
            @Value("${badrlink.gateway.rate-limit.default-burst-capacity:20}") int defaultBurstCapacity) {
        RedisRateLimiter rateLimiter = new RedisRateLimiter(defaultReplenishRate, defaultBurstCapacity, 1);
        rateLimiter.setIncludeHeaders(true);
        return rateLimiter;
    }
}
