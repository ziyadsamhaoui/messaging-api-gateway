package com.ziyadsamhaoui.messagingapigateway.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Edge policy configuration owned by the gateway (routing itself lives in the YAML route table).
 *
 * @param security token verification and internal-path hardening
 * @param cors     browser origin policy for the public edge
 */
@Validated
@ConfigurationProperties(prefix = "badrlink.gateway")
public record BadrLinkGatewayProperties(@Valid Security security, @Valid Cors cors) {

    /**
     * @param publicPaths          request paths served without an access token
     * @param internalPathSegment  path segment that must never be reachable from the public edge
     * @param internalTokenHeader  header reserved for service-to-service calls, stripped from clients
     * @param jwt                  token verification settings
     */
    public record Security(
            @NotEmpty List<String> publicPaths,
            @DefaultValue("internal") String internalPathSegment,
            @DefaultValue("X-Internal-Token") String internalTokenHeader,
            @Valid Jwt jwt) {
    }

    /**
     * @param jwkSetUri URI of the Auth Service JWKS document used to verify RS256 signatures
     * @param issuer    expected {@code iss} claim value
     */
    public record Jwt(@NotBlank String jwkSetUri, @NotBlank String issuer) {
    }

    /**
     * @param allowedOrigins exact frontend origins; wildcards are never used together with credentials
     * @param allowedMethods HTTP methods allowed from the browser
     * @param allowedHeaders request headers allowed from the browser
     * @param exposedHeaders response headers readable by browser JavaScript
     * @param maxAge         how long a preflight result may be cached
     */
    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            @NotEmpty List<String> exposedHeaders,
            @DefaultValue("3600s") Duration maxAge) {
    }
}
