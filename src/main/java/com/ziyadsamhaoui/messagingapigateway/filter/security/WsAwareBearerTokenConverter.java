package com.ziyadsamhaoui.messagingapigateway.filter.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;


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
