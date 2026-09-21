package com.ziyadsamhaoui.messagingapigateway.filter.global;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ziyadsamhaoui.messagingapigateway.config.BadrLinkGatewayProperties;
import com.ziyadsamhaoui.messagingapigateway.exception.GatewayErrorWriter;

import reactor.core.publisher.Mono;

/**
 * Strips the internal service-to-service credential ({@code X-Internal-Token}, configurable) from
 * every inbound client request before routing.
 *
 * <p>Without this, any client could set the header themselves and impersonate another service once
 * the request reaches a downstream service. The gateway never injects the header itself: internal
 * calls do not traverse the public edge.
 */
@Component
public class InternalHeaderStripFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalHeaderStripFilter.class);

    private final String internalTokenHeader;

    public InternalHeaderStripFilter(BadrLinkGatewayProperties properties) {
        this.internalTokenHeader = properties.security().internalTokenHeader();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getHeaders().containsHeader(this.internalTokenHeader)) {
            return chain.filter(exchange);
        }
        log.warn("Stripped client-supplied {} header on {} {} from {}", this.internalTokenHeader,
                exchange.getRequest().getMethod(), GatewayErrorWriter.requestPath(exchange),
                GatewayErrorWriter.clientAddress(exchange));
        // The header value is never logged: it is a credential.
        ServerWebExchange sanitized = exchange.mutate()
                .request(request -> request.headers(headers -> headers.remove(this.internalTokenHeader)))
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
