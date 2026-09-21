package com.ziyadsamhaoui.messagingapigateway.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Guards the YAML route table at startup.
 *
 * <p>Spring Cloud Gateway already refuses to start on a syntactically invalid route, but it happily
 * accepts a route that is missing its rate limiter or that exposes an internal path. Both are edge
 * misconfigurations that would only be noticed in production, so they fail the boot instead.
 */
@Configuration
public class GatewayRouteConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouteConfig.class);

    private static final String RATE_LIMITER_FILTER = "RequestRateLimiter";

    private static final String KEY_RESOLVER_ARG = "key-resolver";

    private static final String BEAN_REFERENCE_PREFIX = "#{@";

    private static final String PATH_PREDICATE = "Path";

    @Bean
    public ApplicationRunner routeTableInvariants(RouteDefinitionLocator routeDefinitionLocator,
            BadrLinkGatewayProperties properties) {
        return args -> {
            List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();
            if (routes == null || routes.isEmpty()) {
                throw new IllegalStateException("Gateway route table is empty: the edge would reject every request");
            }
            List<String> violations = new ArrayList<>();
            for (RouteDefinition route : routes) {
                violations.addAll(validate(route, properties.security().internalPathSegment()));
            }
            if (!violations.isEmpty()) {
                throw new IllegalStateException("Invalid gateway route table: " + violations);
            }
            routes.forEach(route -> log.info("Route '{}' uri={} predicates={} filters={}", route.getId(), route.getUri(),
                    route.getPredicates(), route.getFilters()));
        };
    }

    private List<String> validate(RouteDefinition route, String internalPathSegment) {
        List<String> violations = new ArrayList<>();
        List<FilterDefinition> rateLimiters = route.getFilters()
                .stream()
                .filter(filter -> RATE_LIMITER_FILTER.equals(filter.getName()))
                .toList();
        if (rateLimiters.isEmpty()) {
            violations.add("route '" + route.getId() + "' has no " + RATE_LIMITER_FILTER + " filter");
        }
        else if (rateLimiters.size() > 1) {
            violations.add("route '" + route.getId() + "' declares " + RATE_LIMITER_FILTER + " more than once");
        }
        else {
            String keyResolver = rateLimiters.getFirst().getArgs().get(KEY_RESOLVER_ARG);
            if (keyResolver == null || !keyResolver.startsWith(BEAN_REFERENCE_PREFIX)) {
                violations.add("route '" + route.getId() + "' must reference a key resolver bean, e.g. "
                        + BEAN_REFERENCE_PREFIX + "userKeyResolver}");
            }
        }
        if (targetsInternalPath(route, internalPathSegment)) {
            violations.add("route '" + route.getId() + "' matches the reserved internal segment '"
                    + internalPathSegment + "'");
        }
        return violations;
    }

    private boolean targetsInternalPath(RouteDefinition route, String internalPathSegment) {
        String reserved = "/" + internalPathSegment.toLowerCase(Locale.ROOT);
        for (PredicateDefinition predicate : route.getPredicates()) {
            if (!PATH_PREDICATE.equals(predicate.getName())) {
                continue;
            }
            for (String pattern : predicate.getArgs().values()) {
                if (pattern != null && pattern.toLowerCase(Locale.ROOT).contains(reserved)) {
                    return true;
                }
            }
        }
        return false;
    }
}
