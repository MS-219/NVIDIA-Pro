package com.juxin.orin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketConfigTest {

    @Test
    void terminalSessionsOutliveInfrastructureIdleTimeouts() {
        assertTrue(WebSocketConfig.WEBSOCKET_IDLE_TIMEOUT_MILLIS >= 3_600_000L);
    }
}
