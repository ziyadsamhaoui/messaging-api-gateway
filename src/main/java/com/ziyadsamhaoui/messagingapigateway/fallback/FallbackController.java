package com.ziyadsamhaoui.messagingapigateway.fallback;

import java.net.URI;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.ziyadsamhaoui.messagingapigateway.dto.ErrorResponse;
import com.ziyadsamhaoui.messagingapigateway.exception.ErrorCode;
import com.ziyadsamhaoui.messagingapigateway.filter.global.GlobalLoggingFilter;

import reactor.core.publisher.Mono;

/**
 * Terminal handler for the circuit breaker's {@code fallbackUri: forward:/fallback}.
 *
 * <p>When an upstream is timing out, failing, or its circuit is open, the client gets a truthful
 * {@code 503} with the standard error body (and the correlation id) instead of a hanging connection or
 * a Netty stack trace. Exposed publicly on purpose: it tells the caller nothing about internals.
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<ErrorResponse>> fallback(ServerWebExchange exchange) {
        String originalPath = originalPath(exchange);
        String correlationId = exchange.getRequest().getHeaders().getFirst(GlobalLoggingFilter.CORRELATION_HEADER);
        log.warn("Circuit breaker fallback for {} {} [{}]", exchange.getRequest().getMethod(), originalPath,
                correlationId);
        ErrorResponse body = ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.UPSTREAM_UNAVAILABLE.name(), "Upstream service is temporarily unavailable", originalPath);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }

    /**
     * @return the client-facing path that was being routed, before the fallback forward happened
     */
    private String originalPath(ServerWebExchange exchange) {
        Set<URI> originalUris = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUris != null && !originalUris.isEmpty()) {
            URI original = originalUris.iterator().next();
            return original.getPath();
        }
        return exchange.getRequest().getPath().value();
    }
}
