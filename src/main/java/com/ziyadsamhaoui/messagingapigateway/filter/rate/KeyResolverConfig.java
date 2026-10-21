package com.ziyadsamhaoui.messagingapigateway.filter.rate;

import java.util.Optional;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {

    static final String ANONYMOUS_KEY = "anonymous";

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse(ANONYMOUS_KEY));
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> isIdentifiable(authentication))
                .map(Authentication::getName)
                .defaultIfEmpty(ANONYMOUS_KEY);
    }

    private static boolean isIdentifiable(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && StringUtils.hasText(authentication.getName());
    }
}
