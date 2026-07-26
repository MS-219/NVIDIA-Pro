-- 镜像授权管理：用于阻止无授权旧镜像注册新设备
CREATE TABLE IF NOT EXISTS image_license (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_key VARCHAR(64) NOT NULL COMMENT '镜像授权码',
    name VARCHAR(100) NOT NULL COMMENT '镜像名称',
    image_version VARCHAR(64) COMMENT '镜像版本',
    status VARCHAR(32) DEFAULT 'active' COMMENT 'active/revoked',
    remark VARCHAR(255) COMMENT '备注',
    created_by VARCHAR(64) COMMENT '创建人',
    factory_username VARCHAR(64) COMMENT '绑定工厂账号',
    revoked_at DATETIME COMMENT '销毁时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_license_key (license_key),
    INDEX idx_factory_username (factory_username),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镜像授权';

CREATE TABLE IF NOT EXISTS image_license_activation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_id BIGINT NOT NULL COMMENT '镜像授权ID',
    license_key VARCHAR(64) NOT NULL COMMENT '镜像授权码',
    device_sn VARCHAR(64) NOT NULL COMMENT '设备SN',
    device_id BIGINT COMMENT '设备ID',
    hardware_fingerprint VARCHAR(128) COMMENT '硬件指纹',
    agent_version VARCHAR(64) COMMENT 'Agent版本',
    image_version VARCHAR(64) COMMENT '镜像版本',
    ip VARCHAR(64) COMMENT '最近上报IP',
    cpu_model VARCHAR(255) COMMENT 'CPU型号',
    first_seen_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_license_device (license_key, device_sn),
    INDEX idx_license_id (license_id),
    INDEX idx_device_sn (device_sn),
    INDEX idx_last_seen_at (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镜像授权激活记录';

ALTER TABLE device
    ADD COLUMN image_license_key VARCHAR(64) NULL COMMENT '镜像授权码',
    ADD COLUMN image_version VARCHAR(64) NULL COMMENT '镜像版本';

CREATE INDEX idx_device_image_license_key ON device (image_license_key);
