package com.ziyadsamhaoui.messagingapigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Hooks;

/**
 * Makes the OpenTelemetry context survive the Gateway's thread hops.
 *
 * <p>The Java agent stores the server span in the Reactor {@code Context} when the request is
 * received, but without automatic propagation the span is not restored around operators that
 * run on another thread, so a filter or an outbound call would see no active span. Enabling the
 * hook restores it, which is what lets the filter chain read the trace id and the downstream
 * WebClient continuing the same trace.
 *
 * <p>Calling it is harmless when the agent is absent (tests, local run without instrumentation):
 * the hook is a no-op around an empty context.
 */
@Configuration
public class ReactorContextConfig {

    private static final Logger log = LoggerFactory.getLogger(ReactorContextConfig.class);

    @PostConstruct
    void enableAutomaticContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        log.info("Reactor automatic context propagation enabled for trace context");
    }
}
