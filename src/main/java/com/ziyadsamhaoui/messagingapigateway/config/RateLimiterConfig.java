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

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${badrlink.gateway.rate-limit.default-replenish-rate:10}") int defaultReplenishRate,
            @Value("${badrlink.gateway.rate-limit.default-burst-capacity:20}") int defaultBurstCapacity) {
        RedisRateLimiter rateLimiter = new RedisRateLimiter(defaultReplenishRate, defaultBurstCapacity, 1);
        rateLimiter.setIncludeHeaders(true);
        return rateLimiter;
    }

    @Bean
    @Primary
    public RateLimiter<RedisRateLimiter.Config> instrumentedRateLimiter(
            RedisRateLimiter redisRateLimiter, MeterRegistry meterRegistry) {
        return new InstrumentedRateLimiter(redisRateLimiter, meterRegistry);
    }
}
