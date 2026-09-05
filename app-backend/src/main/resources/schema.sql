CREATE TABLE IF NOT EXISTS app_user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL,
    nickname VARCHAR(40) NOT NULL DEFAULT '聚芯用户',
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
    name VARCHAR(80) NOT NULL DEFAULT '聚芯节点',
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

CREATE TABLE IF NOT EXISTS app_edge_command (
    id BIGINT NOT NULL AUTO_INCREMENT,
    command_no VARCHAR(64) NOT NULL,
    device_sn VARCHAR(64) NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    command_text VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    exit_code INT NULL,
    result_text TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_edge_command_no (command_no),
    KEY idx_app_edge_command_device_status (device_sn, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- APP management data is intentionally separate from the legacy Orin schema.
CREATE TABLE IF NOT EXISTS app_admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_username VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(80) NULL,
    detail TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_admin_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_earning_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    node_id BIGINT NULL,
    amount DECIMAL(18,8) NOT NULL,
    earning_date DATE NOT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'node',
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_earning_user_date (user_id, earning_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_wallet_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount DECIMAL(18,8) NOT NULL,
    balance_after DECIMAL(18,8) NOT NULL,
    direction VARCHAR(12) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_app_wallet_idempotency (idempotency_key),
    KEY idx_app_wallet_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_withdrawal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount DECIMAL(18,8) NOT NULL,
    method VARCHAR(24) NOT NULL,
    account_name VARCHAR(80) NULL,
    account_no VARCHAR(128) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    review_note VARCHAR(255) NULL,
    reviewed_by VARCHAR(80) NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_withdrawal_status_created (status, created_at),
    KEY idx_app_withdrawal_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_payment_apply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    method VARCHAR(24) NOT NULL,
    account_name VARCHAR(80) NOT NULL,
    account_no VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    review_note VARCHAR(255) NULL,
    reviewed_by VARCHAR(80) NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_payment_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'draft',
    pinned TINYINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP NULL,
    created_by VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_notice_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(40) NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    reply TEXT NULL,
    handled_by VARCHAR(80) NULL,
    handled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_feedback_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_invite_relation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT NOT NULL,
    invite_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_app_invitee (invitee_user_id), KEY idx_app_inviter (inviter_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_reward_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source_user_id BIGINT NULL,
    amount DECIMAL(18,8) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'settled',
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_reward_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_exchange_product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    description TEXT NULL,
    price DECIMAL(18,8) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    image_url VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_exchange_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_exchange_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(48) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    amount DECIMAL(18,8) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    address_snapshot TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_app_exchange_order_no (order_no), KEY idx_app_exchange_order_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_exchange_logistics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    carrier VARCHAR(80) NOT NULL,
    tracking_no VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'shipped',
    shipped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_app_logistics_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_device_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_no VARCHAR(48) NOT NULL,
    device_sn VARCHAR(64) NULL,
    task_type VARCHAR(40) NOT NULL,
    payload TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    result_text TEXT NULL,
    created_by VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_app_device_task_no (task_no), KEY idx_app_device_task_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_device_upgrade_package (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    download_url VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    release_note TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_upgrade_package_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_device_upgrade_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    package_id BIGINT NOT NULL,
    device_sn VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    result_text TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (id), KEY idx_app_upgrade_record_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_mobile_release (
    id BIGINT NOT NULL AUTO_INCREMENT,
    platform VARCHAR(16) NOT NULL DEFAULT 'android',
    version VARCHAR(64) NOT NULL,
    version_code INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    release_note TEXT NULL,
    force_update BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    storage_path VARCHAR(500) NOT NULL,
    download_path VARCHAR(500) NOT NULL,
    published_at TIMESTAMP NULL,
    created_by VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_app_mobile_release_platform_code (platform, version_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_system_setting (
    setting_key VARCHAR(80) NOT NULL,
    setting_value TEXT NOT NULL,
    updated_by VARCHAR(80) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
