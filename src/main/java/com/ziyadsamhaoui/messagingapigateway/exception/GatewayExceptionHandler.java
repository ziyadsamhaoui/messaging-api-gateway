package com.ziyadsamhaoui.messagingapigateway.exception;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Replaces the default Spring Boot error handler so that everything thrown inside the reactive
 * gateway pipeline (routing, rate limiting, circuit breaking, upstream transport) leaves the edge as
 * a canonical {@link com.ziyadsamhaoui.messagingapigateway.dto.ErrorResponse}.
 *
 * <p>Ordered before {@code DefaultErrorWebExceptionHandler} (-1) so it always wins.
 */
@Order(-2)
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /**
     * Upstream transport failures reach the reactive pipeline as several different Netty/WebClient
     * types depending on the HTTP client in use. Classifying them by simple name keeps the gateway
     * decoupled from that implementation detail while still reporting a truthful 503.
     */
    private static final Set<String> UPSTREAM_FAILURE_TYPES = Set.of(
            "ConnectException", "ConnectTimeoutException", "AnnotatedConnectException",
            "ReadTimeoutException", "WriteTimeoutException", "TimeoutException",
            "WebClientRequestException", "PrematureCloseException",
            "UnknownHostException", "ClosedChannelException");

    private static final Set<String> RATE_LIMITER_FAILURE_TYPES = Set.of(
            "RedisConnectionFailureException", "RedisSystemException", "QueryTimeoutException",
            "RedisCommandTimeoutException");

    private static final int MAX_CAUSE_DEPTH = 8;

    private final GatewayErrorWriter errorWriter;

    public GatewayExceptionHandler(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }
        Failure failure = classify(throwable);
        if (failure.status().is5xxServerError()) {
            log.error("Gateway failure {} on {} {} -> {} {}", failure.code(), exchange.getRequest().getMethod(),
                    GatewayErrorWriter.requestPath(exchange), failure.status().value(), failure.message(), throwable);
        }
        else {
            log.debug("Gateway rejected {} {} -> {} {} ({})", exchange.getRequest().getMethod(),
                    GatewayErrorWriter.requestPath(exchange), failure.status().value(), failure.code(),
                    throwable.getClass().getSimpleName());
        }
        return this.errorWriter.write(exchange, failure.status(), failure.code(), failure.message());
    }

    private Failure classify(Throwable throwable) {
        if (throwable instanceof ResponseStatusException statusException) {
            HttpStatusCode statusCode = statusException.getStatusCode();
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            if (status == null) {
                return Failure.internal();
            }
            String reason = statusException.getReason();
            return new Failure(status, ErrorCode.fromStatus(status), (reason != null) ? reason : status.getReasonPhrase());
        }
        if (matchesAnyType(throwable, RATE_LIMITER_FAILURE_TYPES)) {
            return new Failure(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.RATE_LIMITER_UNAVAILABLE,
                    "Rate limiter is unavailable, request rejected");
        }
        if (matchesAnyType(throwable, UPSTREAM_FAILURE_TYPES)) {
            return new Failure(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UPSTREAM_UNAVAILABLE,
                    "Upstream service is unavailable");
        }
        return Failure.internal();
    }

    private static boolean matchesAnyType(Throwable throwable, Set<String> simpleTypeNames) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (simpleTypeNames.contains(current.getClass().getSimpleName())) {
                return true;
            }
            current = (current.getCause() != current) ? current.getCause() : null;
        }
        return false;
    }

    private record Failure(HttpStatus status, ErrorCode code, String message) {

        static Failure internal() {
            return new Failure(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "Unexpected gateway error");
        }
    }
}
