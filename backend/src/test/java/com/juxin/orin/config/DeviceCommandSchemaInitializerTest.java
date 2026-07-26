package com.juxin.orin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceCommandSchemaInitializerTest {

    @Test
    void createTableSqlContainsRequiredCommandColumnsAndIndexes() {
        String sql = DeviceCommandSchemaInitializer.CREATE_TABLE_SQL;

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS device_command"));
        assertTrue(sql.contains("command_no VARCHAR(64) NOT NULL"));
        assertTrue(sql.contains("command_text TEXT NOT NULL"));
        assertTrue(sql.contains("INDEX idx_create_time (create_time)"));
    }
}
