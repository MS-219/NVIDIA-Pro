package com.juxin.orin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

/** Adds optional per-user earnings ranges to existing databases. */
@Slf4j
@Component
public class AppUserEarningsSchemaInitializer {

    static final String ADD_MIN_AMOUNT_SQL = "ALTER TABLE app_user "
            + "ADD COLUMN daily_earnings_min DECIMAL(16,2) NULL COMMENT '个人每天基础收益最低金额' AFTER level_manual";
    static final String ADD_MAX_AMOUNT_SQL = "ALTER TABLE app_user "
            + "ADD COLUMN daily_earnings_max DECIMAL(16,2) NULL COMMENT '个人每天基础收益最高金额' AFTER daily_earnings_min";

    private final JdbcOperations jdbcTemplate;

    public AppUserEarningsSchemaInitializer(JdbcOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        addColumnIfMissing("daily_earnings_min", ADD_MIN_AMOUNT_SQL);
        addColumnIfMissing("daily_earnings_max", ADD_MAX_AMOUNT_SQL);
        log.info("app_user 个人收益字段检查完成");
    }

    private void addColumnIfMissing(String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user' AND COLUMN_NAME = ?",
                Integer.class,
                columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }
}
