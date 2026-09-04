package com.juxin.orin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeviceOfflinePeriodSchemaInitializer {

    static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS device_offline_period (
                id BIGINT NOT NULL AUTO_INCREMENT,
                device_id BIGINT NOT NULL,
                offline_start DATETIME NOT NULL,
                online_at DATETIME NULL,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                UNIQUE KEY uk_device_offline_start (device_id, offline_start),
                KEY idx_device_offline_range (device_id, offline_start, online_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
            """;

    private final JdbcOperations jdbcTemplate;

    public DeviceOfflinePeriodSchemaInitializer(JdbcOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        log.info("device_offline_period 数据表检查完成");
    }
}
