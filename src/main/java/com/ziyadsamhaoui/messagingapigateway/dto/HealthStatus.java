package com.ziyadsamhaoui.messagingapigateway.dto;

import java.time.Instant;

public record HealthStatus(String service, String status, int routeCount, Instant timestamp) {

    public static HealthStatus up(String service, int routeCount) {
        return new HealthStatus(service, "UP", routeCount, Instant.now());
    }
}
