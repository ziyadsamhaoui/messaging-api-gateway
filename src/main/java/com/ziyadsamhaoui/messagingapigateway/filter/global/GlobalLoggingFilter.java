package com.ziyadsamhaoui.messagingapigateway.filter.global;

import java.util.concurrent.TimeUnit;

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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

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

    /**
     * The trace id of the inbound request is the correlation id: the agent creates the server
     * span before the gateway filter chain runs, and automatic Reactor context propagation keeps
     * that span current here. Downstream services then receive the same value through the header
     * while the W3C traceparent keeps them on the same trace.
     */
    private String resolveCorrelationId(ServerWebExchange exchange) {
        String supplied = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        SpanContext spanContext = Span.current().getSpanContext();
        String traceId = spanContext.isValid() ? spanContext.getTraceId() : null;
        return CorrelationIdResolver.resolve(supplied, traceId);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
