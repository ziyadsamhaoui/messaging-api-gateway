package com.ziyadsamhaoui.messagingapigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
