package com.ziyadsamhaoui.messagingapigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Hooks;

@Configuration
public class ReactorContextConfig {

    private static final Logger log = LoggerFactory.getLogger(ReactorContextConfig.class);

    @PostConstruct
    void enableAutomaticContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        log.info("Reactor automatic context propagation enabled for trace context");
    }
}
