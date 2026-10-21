package com.ziyadsamhaoui.messagingapigateway.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    RATE_LIMITER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return this.status;
    }

    public static ErrorCode fromStatus(HttpStatus status) {
        for (ErrorCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        return status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
    }
}
