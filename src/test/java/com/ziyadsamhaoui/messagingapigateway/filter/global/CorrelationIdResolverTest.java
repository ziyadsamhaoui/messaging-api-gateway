package com.ziyadsamhaoui.messagingapigateway.filter.global;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdResolverTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void traceIdWinsSoSupportAndJaegerSeeTheSameValue() {
        assertThat(CorrelationIdResolver.resolve("some-client-correlation", TRACE_ID))
                .isEqualTo(TRACE_ID);
    }

    @Test
    void suppliedCorrelationIdIsKeptWhenThereIsNoTrace() {
        assertThat(CorrelationIdResolver.resolve("it-correlation-1234", null))
                .isEqualTo("it-correlation-1234");
    }

    @Test
    void unusableSuppliedValueIsReplacedRatherThanForwarded() {
        assertThat(CorrelationIdResolver.resolve("contains spaces and is longer than allowed", null))
                .isNotBlank()
                .doesNotContain(" ");
    }

    @Test
    void randomFallbackIsAlwaysPresentForTheResponseContract() {
        assertThat(CorrelationIdResolver.resolve(null, null)).hasSize(36);
        assertThat(CorrelationIdResolver.resolve(null, "00000000000000000000000000000000")).hasSize(36);
        assertThat(CorrelationIdResolver.resolve(null, "not-a-trace-id")).hasSize(36);
    }
}
