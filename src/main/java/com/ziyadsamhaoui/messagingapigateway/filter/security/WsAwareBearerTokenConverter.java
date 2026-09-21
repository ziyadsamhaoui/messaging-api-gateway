package com.ziyadsamhaoui.messagingapigateway.filter.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Bearer token extraction that additionally accepts {@code ?access_token=} for the
 * {@code /ws/**} WebSocket handshake, because browser {@code WebSocket}/{@code SockJS} clients cannot
 * set an {@code Authorization} header.
 *
 * <p>Every other path keeps the strict header-only behaviour, so a query-string token can never
 * authorise a regular REST call (query strings leak into access logs and referrers).
 *
 * <p>Accepting the token here is what makes the {@code UserKeyResolver} see a subject for WebSocket
 * handshakes, so each realtime connection gets its own rate-limit bucket instead of sharing one.
 */
public class WsAwareBearerTokenConverter implements ServerAuthenticationConverter {

    private final ServerBearerTokenAuthenticationConverter headerOnlyConverter = new ServerBearerTokenAuthenticationConverter();

    private final ServerBearerTokenAuthenticationConverter wsConverter = new ServerBearerTokenAuthenticationConverter();

    public WsAwareBearerTokenConverter() {
        this.wsConverter.setAllowUriQueryParameter(true);
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        boolean webSocketHandshake = exchange.getRequest().getPath().value().startsWith(JwtAuthenticationFilter.WS_PATH_PREFIX);
        return (webSocketHandshake ? this.wsConverter : this.headerOnlyConverter).convert(exchange);
    }
}
