package com.ziyadsamhaoui.messagingapigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes shared by the gateway's security filters, rate limiter and
 * exception handler. Clients branch on {@code code}; humans read {@code message}.
 */
public enum ErrorCode {

    /** Missing, malformed, expired or signature-invalid access token. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    /** Authenticated principal is not allowed to perform the operation. */
    FORBIDDEN(HttpStatus.FORBIDDEN),
    /** Unknown resource, unknown route, or a blocked internal path (indistinguishable by design). */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Caller exceeded its token bucket. */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    /** Target upstream is unreachable, timed out, or its circuit breaker is open. */
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** Gateway could not reach Redis to evaluate the token bucket (fail-closed). */
    RATE_LIMITER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** Malformed request rejected at the edge. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    /** Unhandled server-side failure. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return this.status;
    }

    /** Best-effort mapping for exceptions that only carry a status code. */
    public static ErrorCode fromStatus(HttpStatus status) {
        for (ErrorCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        return status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
    }
}
