package com.juxin.orin.app.config;

import com.juxin.orin.app.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcOperations jdbc;

    public HealthController(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        boolean databaseUp;
        try {
            databaseUp = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException ignored) {
            databaseUp = false;
        }
        Map<String, Object> details = Map.of(
                "status", databaseUp ? "UP" : "DOWN",
                "database", databaseUp ? "UP" : "DOWN");
        if (databaseUp) {
            return ResponseEntity.ok(ApiResponse.success(details));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(HttpStatus.SERVICE_UNAVAILABLE.value(), "数据库不可用", details));
    }
}
