package com.juxin.orin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

/** Adds per-device enrollment credentials to both fresh and existing databases. */
@Slf4j
@Component
public class DeviceEnrollmentSchemaInitializer {

    static final String ADD_TOKEN_HASH_SQL = "ALTER TABLE device "
            + "ADD COLUMN device_token_hash CHAR(64) NULL COMMENT '设备令牌SHA-256' AFTER image_version";
    static final String ADD_TOKEN_SEED_SQL = "ALTER TABLE device "
            + "ADD COLUMN device_token_seed CHAR(64) NULL COMMENT '设备令牌恢复随机种子' AFTER device_token_hash";
    static final String ADD_FINGERPRINT_SQL = "ALTER TABLE device "
            + "ADD COLUMN hardware_fingerprint VARCHAR(128) NULL COMMENT '首次入网硬件指纹' AFTER device_token_seed";
    static final String ADD_ENROLLED_AT_SQL = "ALTER TABLE device "
            + "ADD COLUMN enrolled_at DATETIME NULL COMMENT '安全入网时间' AFTER hardware_fingerprint";
    static final String ADD_TOKEN_INDEX_SQL = "ALTER TABLE device "
            + "ADD UNIQUE INDEX uk_device_token_hash (device_token_hash)";
    static final String ADD_FINGERPRINT_INDEX_SQL = "ALTER TABLE device "
            + "ADD UNIQUE INDEX uk_device_hardware_fingerprint (hardware_fingerprint)";
    static final String BACKFILL_FINGERPRINT_SQL = """
            UPDATE device d
            JOIN image_license_activation a ON a.id = (
                SELECT a2.id
                FROM image_license_activation a2
                WHERE a2.device_sn = d.sn AND a2.hardware_fingerprint IS NOT NULL
                ORDER BY a2.last_seen_at DESC, a2.id DESC
                LIMIT 1
            )
            SET d.hardware_fingerprint = a.hardware_fingerprint
            WHERE d.hardware_fingerprint IS NULL
            """;

    private final JdbcOperations jdbcTemplate;

    public DeviceEnrollmentSchemaInitializer(JdbcOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        addColumnIfMissing("device_token_hash", ADD_TOKEN_HASH_SQL);
        addColumnIfMissing("device_token_seed", ADD_TOKEN_SEED_SQL);
        addColumnIfMissing("hardware_fingerprint", ADD_FINGERPRINT_SQL);
        addColumnIfMissing("enrolled_at", ADD_ENROLLED_AT_SQL);
        addIndexIfMissing("uk_device_token_hash", ADD_TOKEN_INDEX_SQL);

        if (tableExists("image_license_activation")) {
            jdbcTemplate.update(BACKFILL_FINGERPRINT_SQL);
        }
        addIndexIfMissing("uk_device_hardware_fingerprint", ADD_FINGERPRINT_INDEX_SQL);
        log.info("device 安全入网字段检查完成");
    }

    private void addColumnIfMissing(String columnName, String sql) {
        if (!columnExists(columnName)) {
            jdbcTemplate.execute(sql);
        }
    }

    private void addIndexIfMissing(String indexName, String sql) {
        if (!indexExists(indexName)) {
            jdbcTemplate.execute(sql);
        }
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device' AND COLUMN_NAME = ?",
                Integer.class,
                columnName);
        return count != null && count > 0;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device' AND INDEX_NAME = ?",
                Integer.class,
                indexName);
        return count != null && count > 0;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }
}
