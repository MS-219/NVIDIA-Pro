package com.juxin.orin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void reportsUpWhenDatabaseResponds() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        var response = new HealthController(jdbcOperations, "test-version").health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("UP", response.getBody().get("database"));
        assertEquals("test-version", response.getBody().get("version"));
    }

    @Test
    void reportsUnavailableWhenDatabaseFails() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("database unavailable"));

        var response = new HealthController(jdbcOperations, "test-version").health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("DOWN", response.getBody().get("database"));
    }
}
