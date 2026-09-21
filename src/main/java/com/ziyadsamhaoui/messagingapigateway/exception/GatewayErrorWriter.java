package com.ziyadsamhaoui.messagingapigateway.exception;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ziyadsamhaoui.messagingapigateway.dto.ErrorResponse;

import reactor.core.publisher.Mono;

import tools.jackson.databind.ObjectMapper;

/**
 * Single place where the gateway turns a failure into the canonical {@link ErrorResponse} body.
 * Used by the security filters (401/403), the exception handler and the circuit breaker fallback so
 * every error leaving the edge has an identical shape.
 */
@Component
public class GatewayErrorWriter {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWriter.class);

    private static final int MAX_PATH_LENGTH = 512;

    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String message) {
        return write(exchange, code.status(), code, message);
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            log.debug("Response already committed, cannot render {} {}", status.value(), code);
            return response.setComplete();
        }
        ErrorResponse body = ErrorResponse.of(status.value(), code.name(), message, requestPath(exchange));
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        DataBuffer buffer = response.bufferFactory().wrap(serialize(body));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] serialize(ErrorResponse body) {
        try {
            return this.objectMapper.writeValueAsBytes(body);
        }
        catch (RuntimeException ex) {
            log.error("Failed to serialize error response, falling back to plain-text body", ex);
            return "{\"code\":\"INTERNAL_ERROR\"}".getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * @return the request path, stripped of CR/LF and truncated, so it is safe to log and serialize
     */
    public static String requestPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        StringBuilder sanitized = new StringBuilder(Math.min(path.length(), MAX_PATH_LENGTH));
        for (int i = 0; i < path.length() && sanitized.length() < MAX_PATH_LENGTH; i++) {
            char c = path.charAt(i);
            if (c != '\r' && c != '\n' && c > 0x1F) {
                sanitized.append(c);
            }
        }
        return sanitized.toString();
    }

    /**
     * @return the client address, used for logging and rate-limit keys
     */
    public static String clientAddress(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        return (remote != null) ? remote.getAddress().getHostAddress() : "unknown";
    }
}
