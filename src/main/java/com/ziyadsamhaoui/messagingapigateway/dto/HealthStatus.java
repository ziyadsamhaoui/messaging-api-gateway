package com.ziyadsamhaoui.messagingapigateway.dto;

import java.time.Instant;

/**
 * Liveness payload exposed on {@code GET /health} for load balancer / Kubernetes probes.
 *
 * @param service   application name as registered with the platform
 * @param status    always {@code UP} when the gateway process can answer at all
 * @param routeCount number of routes currently installed in the reactive route table
 * @param timestamp server time of the check
 */
public record HealthStatus(String service, String status, int routeCount, Instant timestamp) {

    public static HealthStatus up(String service, int routeCount) {
        return new HealthStatus(service, "UP", routeCount, Instant.now());
    }
}
