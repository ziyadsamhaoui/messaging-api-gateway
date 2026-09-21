package com.ziyadsamhaoui.messagingapigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Strict CORS policy for the browser-facing edge.
 *
 * <p>Only explicitly configured origins are allowed (never {@code *} together with credentials), and
 * the rate-limit headers are exposed so single-page clients can implement back-off. The filter runs as
 * a WebFilter on the Netty pipeline, which means a preflight {@code OPTIONS} is answered at the edge
 * without ever reaching Spring Security or an upstream service.
 *
 * <p>The internal token header is deliberately <em>not</em> an allowed request header: browser clients
 * can never send service-to-service credentials.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(BadrLinkGatewayProperties properties) {
        BadrLinkGatewayProperties.Cors cors = properties.cors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(cors.allowedOrigins());
        configuration.setAllowedMethods(cors.allowedMethods());
        configuration.setAllowedHeaders(cors.allowedHeaders());
        configuration.setExposedHeaders(cors.exposedHeaders());
        configuration.setMaxAge(cors.maxAge());
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }
}
