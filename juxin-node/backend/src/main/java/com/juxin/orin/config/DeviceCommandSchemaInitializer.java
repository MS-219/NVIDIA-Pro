package com.juxin.orin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures the remote device command table exists before command APIs are used. */
@Slf4j
@Component
public class DeviceCommandSchemaInitializer {

    static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS device_command (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                command_no VARCHAR(64) NOT NULL COMMENT '指令编号',
                device_id BIGINT NULL COMMENT '设备ID',
                device_sn VARCHAR(64) NOT NULL COMMENT '设备SN',
                command_type VARCHAR(64) NOT NULL COMMENT '指令类型',
                command_text TEXT NOT NULL COMMENT '最终下发给 Agent 执行的命令',
                command_payload TEXT NULL COMMENT '结构化参数JSON',
                status VARCHAR(32) DEFAULT 'pending' COMMENT 'pending/delivered/completed/canceled/failed',
                exit_code INT NULL COMMENT '进程退出码',
                result_text TEXT NULL COMMENT '执行输出或错误摘要',
                remark VARCHAR(255) NULL COMMENT '备注',
                dispatched_at DATETIME NULL COMMENT '下发到设备时间',
                finished_at DATETIME NULL COMMENT '执行完成时间',
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE INDEX uk_command_no (command_no),
                INDEX idx_device_sn (device_sn),
                INDEX idx_status (status),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备远程指令记录'
            """;

    private final JdbcTemplate jdbcTemplate;

    public DeviceCommandSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        log.info("device_command 数据表检查完成");
    }
}
