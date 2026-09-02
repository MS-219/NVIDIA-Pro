package com.juxin.orin.app.config;

import com.juxin.orin.app.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void reportsUpWhenDatabaseResponds() {
        JdbcOperations jdbc = jdbcReturning(1, null);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = new HealthController(jdbc).health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().code());
        assertEquals("UP", response.getBody().data().get("status"));
        assertEquals("UP", response.getBody().data().get("database"));
    }

    @Test
    void reportsServiceUnavailableWhenDatabaseFails() {
        JdbcOperations jdbc = jdbcReturning(null, new IllegalStateException("database unavailable"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = new HealthController(jdbc).health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, response.getBody().code());
        assertEquals("DOWN", response.getBody().data().get("status"));
        assertEquals("DOWN", response.getBody().data().get("database"));
    }

    private static JdbcOperations jdbcReturning(Object value, RuntimeException failure) {
        return (JdbcOperations) Proxy.newProxyInstance(
                HealthControllerTest.class.getClassLoader(),
                new Class<?>[]{JdbcOperations.class},
                (proxy, method, args) -> {
                    if ("queryForObject".equals(method.getName())) {
                        if (failure != null) {
                            throw failure;
                        }
                        return value;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
