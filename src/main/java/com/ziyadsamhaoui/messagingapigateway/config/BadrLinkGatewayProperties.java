package com.ziyadsamhaoui.messagingapigateway.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "badrlink.gateway")
public record BadrLinkGatewayProperties(@Valid Security security, @Valid Cors cors) {

    public record Security(
            @NotEmpty List<String> publicPaths,
            @DefaultValue("internal") String internalPathSegment,
            @DefaultValue("X-Internal-Token") String internalTokenHeader,
            @Valid Jwt jwt) {
    }

    public record Jwt(@NotBlank String jwkSetUri, @NotBlank String issuer) {
    }

    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            @NotEmpty List<String> exposedHeaders,
            @DefaultValue("3600s") Duration maxAge) {
    }
}
