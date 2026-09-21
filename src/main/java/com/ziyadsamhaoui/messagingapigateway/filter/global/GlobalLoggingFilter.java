package com.ziyadsamhaoui.messagingapigateway.filter.global;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ziyadsamhaoui.messagingapigateway.exception.GatewayErrorWriter;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Correlated access logging for every request that survives the edge security gates.
 *
 * <p>Runs after {@code InternalPathBlockFilter} and {@code InternalHeaderStripFilter} (so blocked
 * traffic is logged by those filters instead) and wraps the whole downstream chain, which means the
 * logged duration includes rate limiting, circuit breaking and the upstream round trip.
 *
 * <p>Access tokens and the {@code X-Internal-Token} header are never logged.
 */
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    /** Correlation id shared by the gateway, the client and downstream services. */
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    /** Accepted shape for a client-supplied correlation id: short, no control characters. */
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = resolveCorrelationId(exchange);
        ServerWebExchange correlated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(CORRELATION_HEADER, correlationId)))
                .build();
        correlated.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);

        String method = correlated.getRequest().getMethod().name();
        String path = GatewayErrorWriter.requestPath(correlated);
        long startNanos = System.nanoTime();

        log.info("--> {} {} [{}] from {}", method, path, correlationId,
                GatewayErrorWriter.clientAddress(correlated));

        return chain.filter(correlated).doFinally(signal -> {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            ServerHttpResponse response = correlated.getResponse();
            HttpStatusCode status = response.getStatusCode();
            if (signal == SignalType.ON_COMPLETE && status != null) {
                log.info("<-- {} {} {} ({} ms) [{}]", method, path, status.value(), elapsedMillis, correlationId);
            }
            else {
                log.info("<-- {} {} {} ({} ms) [{}]", method, path, "FAILED", elapsedMillis, correlationId);
            }
        });
    }

    private String resolveCorrelationId(ServerWebExchange exchange) {
        String supplied = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        if (supplied != null && CORRELATION_ID_PATTERN.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
