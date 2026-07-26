-- 设备远程指令记录（后台内部指令中心）
CREATE TABLE IF NOT EXISTS device_command (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_no VARCHAR(64) NOT NULL COMMENT '指令编号',
    device_id BIGINT COMMENT '设备ID',
    device_sn VARCHAR(64) NOT NULL COMMENT '设备SN',
    command_type VARCHAR(64) NOT NULL COMMENT '指令类型: HEALTH_CHECK/START_PROXY/STOP_PROXY/OPEN_TUNNEL/CLOSE_TUNNEL/RESTART_AGENT/CUSTOM',
    command_text TEXT NOT NULL COMMENT '最终下发给 Agent 执行的命令',
    command_payload TEXT COMMENT '结构化参数JSON',
    status VARCHAR(32) DEFAULT 'pending' COMMENT '状态: pending/delivered/completed/canceled/failed',
    exit_code INT COMMENT '进程退出码',
    result_text TEXT COMMENT '执行输出或错误摘要',
    remark VARCHAR(255) COMMENT '备注',
    dispatched_at DATETIME COMMENT '下发到设备时间',
    finished_at DATETIME COMMENT '执行完成时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_command_no (command_no),
    INDEX idx_device_sn (device_sn),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备远程指令记录';
