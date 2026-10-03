package com.ziyadsamhaoui.messagingapigateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ziyadsamhaoui.messagingapigateway.dto.HealthStatus;

import reactor.core.publisher.Mono;

@RestController
public class HealthController {

    private final RouteDefinitionLocator routeDefinitionLocator;

    private final String serviceName;

    public HealthController(RouteDefinitionLocator routeDefinitionLocator,
            @Value("${spring.application.name:messaging-api-gateway}") String serviceName) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public Mono<HealthStatus> health() {
        return this.routeDefinitionLocator.getRouteDefinitions()
                .count()
                .map(routeCount -> HealthStatus.up(this.serviceName, routeCount.intValue()));
    }
}
