package com.juxin.orin.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeviceOfflinePeriodSchemaInitializerTest {

    @Test
    void createTableSqlContainsRequiredPeriodColumnsAndIndexes() {
        String sql = DeviceOfflinePeriodSchemaInitializer.CREATE_TABLE_SQL;

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS device_offline_period"));
        assertTrue(sql.contains("device_id BIGINT NOT NULL"));
        assertTrue(sql.contains("offline_start DATETIME NOT NULL"));
        assertTrue(sql.contains("online_at DATETIME NULL"));
        assertTrue(sql.contains("UNIQUE KEY uk_device_offline_start (device_id, offline_start)"));
        assertTrue(sql.contains("KEY idx_device_offline_range (device_id, offline_start, online_at)"));
    }

    @Test
    void initializeExecutesIdempotentCreateTableStatement() {
        JdbcOperations jdbcTemplate = mock(JdbcOperations.class);

        new DeviceOfflinePeriodSchemaInitializer(jdbcTemplate).initialize();

        verify(jdbcTemplate).execute(DeviceOfflinePeriodSchemaInitializer.CREATE_TABLE_SQL);
    }
}
