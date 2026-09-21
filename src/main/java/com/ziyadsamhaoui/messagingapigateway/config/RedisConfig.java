package com.ziyadsamhaoui.messagingapigateway.config;

import java.time.Duration;

import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.ClientOptions;

/**
 * Reactive Redis wiring for the token bucket.
 *
 * <p>Rate limiting is fail-closed (ADR-006): if Redis is unreachable the gateway must reject the
 * request with {@code 503 RATE_LIMITER_UNAVAILABLE} rather than queue it or silently allow traffic.
 * Lettuce is therefore configured to reject commands while disconnected and to time out instead of
 * hanging on the event loop.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer redisTimeouts() {
        return builder -> builder
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ofMillis(100));
    }

    @Bean
    public LettuceClientOptionsBuilderCustomizer redisFailFastClientOptions() {
        return options -> options
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
