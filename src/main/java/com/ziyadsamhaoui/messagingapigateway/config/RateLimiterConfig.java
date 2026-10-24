package com.ziyadsamhaoui.messagingapigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.ziyadsamhaoui.messagingapigateway.filter.rate.InstrumentedRateLimiter;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class RateLimiterConfig {

    /**
     * Kept with its original name and declared type {@link RedisRateLimiter}: Spring Cloud
     * Gateway's {@code GatewayRedisAutoConfiguration} registers a bean of the same name behind
     * {@code @ConditionalOnMissingBean} for that exact type, so relaxing the return type here
     * would let the auto-configuration try to define the bean a second time.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${badrlink.gateway.rate-limit.default-replenish-rate:10}") int defaultReplenishRate,
            @Value("${badrlink.gateway.rate-limit.default-burst-capacity:20}") int defaultBurstCapacity) {
        RedisRateLimiter rateLimiter = new RedisRateLimiter(defaultReplenishRate, defaultBurstCapacity, 1);
        rateLimiter.setIncludeHeaders(true);
        return rateLimiter;
    }

    /**
     * The limiter the {@code RequestRateLimiter} filter actually applies, since that filter is
     * auto-configured with the {@link RateLimiter} bean and this one is primary. The wrapper only
     * observes the decision, so limits, headers and per-route configuration still come from the
     * Redis limiter above.
     */
    @Bean
    @Primary
    public RateLimiter<RedisRateLimiter.Config> instrumentedRateLimiter(
            RedisRateLimiter redisRateLimiter, MeterRegistry meterRegistry) {
        return new InstrumentedRateLimiter(redisRateLimiter, meterRegistry);
    }
}
