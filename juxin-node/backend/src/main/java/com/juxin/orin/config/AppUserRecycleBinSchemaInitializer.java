package com.juxin.orin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures the recycle-bin columns exist before user queries begin. */
@Slf4j
@Component
public class AppUserRecycleBinSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public AppUserRecycleBinSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        if (!columnExists("deleted")) {
            jdbcTemplate.execute("ALTER TABLE app_user "
                    + "ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-回收站'");
            log.info("已为 app_user 创建 deleted 字段");
        }
        if (!columnExists("deleted_at")) {
            jdbcTemplate.execute("ALTER TABLE app_user "
                    + "ADD COLUMN deleted_at DATETIME NULL COMMENT '删除时间'");
            log.info("已为 app_user 创建 deleted_at 字段");
        }
        if (!indexExists("idx_app_user_deleted")) {
            jdbcTemplate.execute("ALTER TABLE app_user ADD INDEX idx_app_user_deleted (deleted, deleted_at)");
            log.info("已为 app_user 创建回收站索引");
        }
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user' AND COLUMN_NAME = ?",
                Integer.class,
                columnName);
        return count != null && count > 0;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user' AND INDEX_NAME = ?",
                Integer.class,
                indexName);
        return count != null && count > 0;
    }
}
