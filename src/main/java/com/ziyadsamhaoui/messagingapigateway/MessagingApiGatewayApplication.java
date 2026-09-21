package com.ziyadsamhaoui.messagingapigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * BadrLink public entry point: reactive edge routing, JWT verification and token-bucket rate limiting.
 *
 * <p>Blocking calls must never be made on this process' event loop: every component in this repository
 * is written against Reactor streams (see ADR-005).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MessagingApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingApiGatewayApplication.class, args);
    }

}
