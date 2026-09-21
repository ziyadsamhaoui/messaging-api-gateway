package com.ziyadsamhaoui.messagingapigateway.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Redis container shared by every integration test in this JVM.
 *
 * <p>A single container is reused across test classes (started lazily, reaped by Testcontainers' Ryuk
 * at JVM exit) so the shared Spring context stays cached and the suite does not pay the container start
 * cost per test class. The token bucket is exercised against a real Redis because the Lua script's
 * behaviour cannot be faked meaningfully.
 */
public final class RedisTestContainer {

    private static final int REDIS_PORT = 6379;

    private static final DockerImageName IMAGE = DockerImageName.parse("redis:7-alpine");

    private static GenericContainer<?> container;

    private RedisTestContainer() {
    }

    public static synchronized String host() {
        start();
        return container.getHost();
    }

    public static synchronized int port() {
        start();
        return container.getMappedPort(REDIS_PORT);
    }

    private static void start() {
        if (container == null) {
            container = new GenericContainer<>(IMAGE)
                    .withExposedPorts(REDIS_PORT)
                    .waitingFor(Wait.forListeningPort());
            container.start();
        }
    }
}
