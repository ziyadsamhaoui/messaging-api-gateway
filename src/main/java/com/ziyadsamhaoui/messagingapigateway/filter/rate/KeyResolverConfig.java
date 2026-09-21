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

/**
 * Key resolvers used by the routes' {@code RequestRateLimiter} filter: public routes are limited per
 * client IP, authenticated routes per token subject.
 *
 * <p>Bean names are referenced from the YAML route table with SpEL, e.g.
 * {@code key-resolver: "#{@userKeyResolver}"}.
 */
@Configuration
public class KeyResolverConfig {

    /**
     * Bucket shared by requests that reached a user-keyed route without an authenticated principal.
     * This should never happen (those routes require a token); it exists so a misconfiguration
     * degrades into a shared throttle instead of an unlimited route.
     */
    static final String ANONYMOUS_KEY = "anonymous";

    /**
     * Client IP, used for the unauthenticated auth surface (register/login/refresh).
     *
     * <p>{@code @Primary} only satisfies the injection point of Spring Cloud Gateway's
     * {@code RequestRateLimiterGatewayFilterFactory}: every route in the YAML table names its resolver
     * explicitly (enforced by {@code GatewayRouteConfig}), and per-client IP is the safer fallback for a
     * route that forgets to.
     *
     * @return a resolver keyed on the client address
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse(ANONYMOUS_KEY));
    }

    /**
     * @return the JWT {@code sub} claim of the authenticated principal (Spring Security exposes it as
     * the authentication name), used for every authenticated route
     */
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
