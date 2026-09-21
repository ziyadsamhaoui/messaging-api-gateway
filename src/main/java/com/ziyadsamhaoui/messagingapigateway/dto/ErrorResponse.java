package com.ziyadsamhaoui.messagingapigateway.dto;

import java.time.Instant;

/**
 * Canonical error body returned by every gateway failure path (security filters, rate limiter
 * and {@code GatewayExceptionHandler}) so clients only ever parse one shape.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-20T12:00:00Z",
 *   "status": 401,
 *   "code": "UNAUTHENTICATED",
 *   "message": "Invalid or expired JWT token",
 *   "path": "/rooms/123"
 * }
 * </pre>
 */
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path) {

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path);
    }
}
