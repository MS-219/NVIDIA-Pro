package com.juxin.orin.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcOperations jdbcOperations;
    private final String version;

    public HealthController(
            JdbcOperations jdbcOperations,
            @Value("${orin.version:development}") String version) {
        this.jdbcOperations = jdbcOperations;
        this.version = version;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean databaseReady;
        try {
            databaseReady = Integer.valueOf(1).equals(jdbcOperations.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException ignored) {
            databaseReady = false;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", databaseReady ? "UP" : "DOWN");
        body.put("application", "juxin-orin-platform");
        body.put("version", version);
        body.put("database", databaseReady ? "UP" : "DOWN");
        body.put("time", OffsetDateTime.now().toString());

        return ResponseEntity.status(databaseReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
