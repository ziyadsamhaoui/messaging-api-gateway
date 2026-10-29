package com.ziyadsamhaoui.messagingapigateway.filter.global;

import java.util.UUID;
import java.util.regex.Pattern;

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
