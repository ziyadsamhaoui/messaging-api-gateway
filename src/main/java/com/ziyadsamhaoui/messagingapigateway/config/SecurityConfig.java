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

/**
 * Security policy of the public edge.
 *
 * <ul>
 * <li>RS256 access tokens are verified against the Auth Service JWKS endpoint; no shared symmetric
 * secret exists in any environment ({@code NimbusReactiveJwtDecoder}).</li>
 * <li>A fast-fail verification filter runs before the authentication filter so an invalid token never
 * reaches a backend, and the original {@code Authorization} header is forwarded untouched.</li>
 * <li>{@code /internal/**} is denied here as well as blocked by {@code InternalPathBlockFilter}:
 * defense in depth (ADR-008).</li>
 * <li>Every rejection renders the same JSON body through {@link GatewayErrorWriter}.</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public ReactiveJwtDecoder gatewayJwtDecoder(BadrLinkGatewayProperties properties) {
        BadrLinkGatewayProperties.Jwt jwt = properties.security().jwt();
        log.info("Access tokens are verified against {} with expected issuer {}", jwt.jwkSetUri(), jwt.issuer());
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwt.jwkSetUri())
                // RS256 only: the Auth Service never signs with a symmetric algorithm.
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // exp/nbf validation plus the issuer check. JWKS keys are cached inside the decoder and
        // refreshed lazily when an unknown kid arrives, so no per-request call to the Auth Service.
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
                // CORS is enforced by the CorsWebFilter bean, ahead of the security chain, so
                // preflight requests never need a token.
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Preflight: allowed through; the CORS filter answers it.
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        // Internal service endpoints are not an authorization problem: the edge must
                        // answer 404 (indistinguishable from an unknown route) instead of confirming
                        // that the path exists. InternalPathBlockFilter terminates these requests
                        // before any routing decision, and no route matches them either way. Rejecting
                        // them here would leak existence as 403 and would also swallow the block
                        // filter's 404 contract (ADR-008).
                        .pathMatchers("/internal/**").permitAll()
                        .pathMatchers(publicPaths).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // /ws/** handshakes may carry the token as a query parameter (browser limitation).
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
