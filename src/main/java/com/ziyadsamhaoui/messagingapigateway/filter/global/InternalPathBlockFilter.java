package com.ziyadsamhaoui.messagingapigateway.filter.global;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ziyadsamhaoui.messagingapigateway.config.BadrLinkGatewayProperties;
import com.ziyadsamhaoui.messagingapigateway.exception.ErrorCode;
import com.ziyadsamhaoui.messagingapigateway.exception.GatewayErrorWriter;

import reactor.core.publisher.Mono;

/**
 * Hard block for service-to-service endpoints ({@code /internal/**}) at the public edge.
 *
 * <p>Defense in depth: even if a route were mistakenly added for an internal path, or a downstream
 * service accepted the request, the request never leaves the gateway. The response is a plain
 * {@code 404} identical to an unknown route so the gateway does not confirm that the path exists.
 */
@Component
public class InternalPathBlockFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalPathBlockFilter.class);

    private final String blockedSegment;

    private final GatewayErrorWriter errorWriter;

    public InternalPathBlockFilter(BadrLinkGatewayProperties properties, GatewayErrorWriter errorWriter) {
        this.blockedSegment = "/" + properties.security().internalPathSegment().toLowerCase(Locale.ROOT) + "/";
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String decodedPath = exchange.getRequest().getPath().value();
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (!isInternalPath(decodedPath) && !isInternalPath(rawPath)) {
            return chain.filter(exchange);
        }
        log.warn("Blocked internal path access: {} {} from {} (route never resolved)",
                exchange.getRequest().getMethod(), GatewayErrorWriter.requestPath(exchange),
                GatewayErrorWriter.clientAddress(exchange));
        return this.errorWriter.write(exchange, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Resource not found");
    }

    private boolean isInternalPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String candidate = path.toLowerCase(Locale.ROOT);
        if (candidate.contains(this.blockedSegment)) {
            return true;
        }
        // Also reject when the reserved segment is the trailing element, e.g. "/users/internal".
        return candidate.endsWith(this.blockedSegment.substring(0, this.blockedSegment.length() - 1));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
