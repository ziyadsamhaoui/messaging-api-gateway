package com.ziyadsamhaoui.messagingapigateway.filter.global;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Chooses the single identifier the edge returns to callers and forwards downstream.
 *
 * <p>The value is the OpenTelemetry trace id of the inbound request whenever a trace exists,
 * so a support ticket, an error body, a log line and a Jaeger trace all carry the same string.
 * A client-supplied header is only used when there is no trace at all (the agent is not
 * attached, for example a local run without instrumentation), and a random UUID is the last
 * resort so the response contract always has a value.
 */
final class CorrelationIdResolver {

    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

    private CorrelationIdResolver() {
    }

    static String resolve(String supplied, String traceId) {
        if (isUsableTraceId(traceId)) {
            return traceId;
        }
        if (supplied != null && CORRELATION_ID_PATTERN.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private static boolean isUsableTraceId(String traceId) {
        return traceId != null
                && TRACE_ID_PATTERN.matcher(traceId).matches()
                && !"00000000000000000000000000000000".equals(traceId);
    }
}
