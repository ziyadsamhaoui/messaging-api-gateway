package com.ziyadsamhaoui.messagingapigateway.filter.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ziyadsamhaoui.messagingapigateway.config.BadrLinkGatewayProperties;
import com.ziyadsamhaoui.messagingapigateway.exception.ErrorCode;
import com.ziyadsamhaoui.messagingapigateway.exception.GatewayErrorWriter;

import reactor.core.publisher.Mono;

/**
 * Edge-level, fail-fast RS256 verification of the access token.
 *
 * <p>Runs inside the Spring Security chain, immediately before the resource-server authentication
 * filter. When the request carries a token, the signature, {@code exp} and {@code iss} claims are
 * checked against the Auth Service JWKS <em>before</em> any routing, rate limiting or upstream call
 * happens, so a forged or expired token never costs a backend request. Requests without a token are
 * left to the authorization rules, which know which paths are public.
 *
 * <p>On success the request is forwarded with the original {@code Authorization} header untouched:
 * the gateway never mints downstream identity headers, every service re-derives the subject from the
 * token (see ADR-007).
 */
public class JwtAuthenticationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /** WebSocket/SockJS handshakes from browsers cannot set headers, so /ws/** also accepts this query parameter. */
    static final String ACCESS_TOKEN_QUERY_PARAM = "access_token";

    static final String WS_PATH_PREFIX = "/ws/";

    private final ReactiveJwtDecoder jwtDecoder;

    private final GatewayErrorWriter errorWriter;

    private final List<PathPattern> publicPaths;

    public JwtAuthenticationFilter(ReactiveJwtDecoder jwtDecoder, GatewayErrorWriter errorWriter,
            BadrLinkGatewayProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.errorWriter = errorWriter;
        this.publicPaths = parsePatterns(properties.security().publicPaths());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(request)) {
            return chain.filter(exchange);
        }
        String token = resolveToken(request);
        if (token == null) {
            // No credentials at all: let the authorization filter reject protected paths with the
            // same canonical body (see SecurityConfig#gatewayAuthenticationEntryPoint).
            return chain.filter(exchange);
        }
        return this.jwtDecoder.decode(token)
                .flatMap(jwt -> chain.filter(exchange))
                .onErrorResume(JwtException.class, ex -> reject(exchange, ex));
    }

    private Mono<Void> reject(ServerWebExchange exchange, JwtException exception) {
        if (isVerifierUnavailable(exception)) {
            log.error("Token verifier unavailable on {} {}: {}", exchange.getRequest().getMethod(),
                    GatewayErrorWriter.requestPath(exchange), exception.getMessage());
            return this.errorWriter.write(exchange, ErrorCode.UPSTREAM_UNAVAILABLE,
                    "Token verification service is unavailable");
        }
        log.debug("Edge JWT verification failed on {} {}: {}", exchange.getRequest().getMethod(),
                GatewayErrorWriter.requestPath(exchange), exception.getMessage());
        return this.errorWriter.write(exchange, ErrorCode.UNAUTHENTICATED, "Invalid or expired JWT token");
    }

    private boolean isPublic(ServerHttpRequest request) {
        PathContainer path = request.getPath();
        for (PathPattern pattern : this.publicPaths) {
            if (pattern.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String value = header.substring(BEARER_PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        if (request.getPath().value().startsWith(WS_PATH_PREFIX)) {
            String queryToken = request.getQueryParams().getFirst(ACCESS_TOKEN_QUERY_PARAM);
            return (queryToken != null && !queryToken.isBlank()) ? queryToken : null;
        }
        return null;
    }

    /**
     * A JWKS fetch failure surfaces as a {@link JwtException} as well; report it as a 503 instead of
     * blaming the caller's token.
     */
    private static boolean isVerifierUnavailable(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof IOException || "WebClientRequestException".equals(current.getClass().getSimpleName())
                    || "TimeoutException".equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = (current.getCause() != current) ? current.getCause() : null;
        }
        return false;
    }

    private static List<PathPattern> parsePatterns(List<String> patterns) {
        PathPatternParser parser = new PathPatternParser();
        List<PathPattern> parsed = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            parsed.add(parser.parse(pattern));
        }
        return List.copyOf(parsed);
    }
}
