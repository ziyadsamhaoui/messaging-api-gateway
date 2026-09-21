package com.ziyadsamhaoui.messagingapigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

import com.ziyadsamhaoui.messagingapigateway.exception.ErrorCode;
import com.ziyadsamhaoui.messagingapigateway.exception.GatewayErrorWriter;
import com.ziyadsamhaoui.messagingapigateway.filter.security.JwtAuthenticationFilter;
import com.ziyadsamhaoui.messagingapigateway.filter.security.WsAwareBearerTokenConverter;


@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public ReactiveJwtDecoder gatewayJwtDecoder(BadrLinkGatewayProperties properties) {
        BadrLinkGatewayProperties.Jwt jwt = properties.security().jwt();
        log.info("Access tokens are verified against {} with expected issuer {}", jwt.jwkSetUri(), jwt.issuer());
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwt.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.issuer()));
        return decoder;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder,
            BadrLinkGatewayProperties properties, GatewayErrorWriter errorWriter) {

        ServerAuthenticationEntryPoint unauthorizedEntryPoint = (exchange, exception) -> {
            log.debug("Rejecting unauthenticated {} {}: {}", exchange.getRequest().getMethod(),
                    GatewayErrorWriter.requestPath(exchange), exception.getMessage());
            return errorWriter.write(exchange, ErrorCode.UNAUTHENTICATED, "Authentication required");
        };

        ServerAccessDeniedHandler accessDeniedHandler = (exchange, deniedException) -> {
            log.debug("Rejecting forbidden {} {}: {}", exchange.getRequest().getMethod(),
                    GatewayErrorWriter.requestPath(exchange), deniedException.getMessage());
            return errorWriter.write(exchange, ErrorCode.FORBIDDEN, "Access denied");
        };

        JwtAuthenticationFilter edgeTokenVerification = new JwtAuthenticationFilter(jwtDecoder, errorWriter, properties);
        String[] publicPaths = properties.security().publicPaths().toArray(String[]::new);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // The gateway is a stateless bearer-token edge: no browser session, no form login.
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/internal/**").permitAll()
                        .pathMatchers(publicPaths).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(new WsAwareBearerTokenConverter())
                        .authenticationEntryPoint(unauthorizedEntryPoint)
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(edgeTokenVerification, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
