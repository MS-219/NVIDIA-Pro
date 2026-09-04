CREATE TABLE IF NOT EXISTS app_user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL,
    nickname VARCHAR(40) NOT NULL DEFAULT 'Orin 用户',
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_account_phone (phone),
    KEY idx_app_user_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sms_login_challenge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    attempts INT NOT NULL DEFAULT 0,
    client_ip VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    provider_request_id VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sms_challenge_phone_created (phone, created_at),
    KEY idx_sms_challenge_ip_created (client_ip, created_at),
    KEY idx_sms_challenge_active (phone, consumed_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Nodes are provisioned by the APP operations team.  A NULL owner means the
-- binding code is available; the mobile client can never create arbitrary
-- nodes or set owner_user_id directly.
CREATE TABLE IF NOT EXISTS app_node (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_code VARCHAR(64) NOT NULL,
    owner_user_id BIGINT NULL,
    name VARCHAR(80) NOT NULL DEFAULT 'Orin 节点',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    hashrate DECIMAL(18,3) NOT NULL DEFAULT 0,
    temperature DECIMAL(6,2) NULL,
    daily_earnings DECIMAL(18,8) NOT NULL DEFAULT 0,
    total_earnings DECIMAL(18,8) NOT NULL DEFAULT 0,
    last_reported_at TIMESTAMP NULL,
    bound_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_node_binding_code (binding_code),
    KEY idx_app_node_owner (owner_user_id),
    KEY idx_app_node_owner_status (owner_user_id, status),
    KEY idx_app_node_last_reported (last_reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_edge_device (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_sn VARCHAR(64) NOT NULL,
    binding_code VARCHAR(64) NOT NULL,
    device_token_hash CHAR(64) NOT NULL,
    hardware_fingerprint CHAR(64) NULL,
    agent_version VARCHAR(64) NULL,
    image_version VARCHAR(128) NULL,
    telemetry_json JSON NULL,
    last_reported_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_edge_device_sn (device_sn),
    UNIQUE KEY uk_app_edge_device_binding_code (binding_code),
    KEY idx_app_edge_device_last_reported (last_reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
