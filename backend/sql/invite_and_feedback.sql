-- =============================================
-- 邀请好友功能数据库结构更新
-- =============================================

-- 1. 在 app_user 表中添加邀请人ID字段
ALTER TABLE app_user ADD COLUMN inviter_id BIGINT NULL COMMENT '邀请人ID';
ALTER TABLE app_user ADD INDEX idx_inviter_id (inviter_id);

-- 2. 创建邀请奖励记录表（记录收益分成）
CREATE TABLE IF NOT EXISTS invite_reward (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    inviter_id BIGINT NOT NULL COMMENT '邀请人ID',
    invitee_id BIGINT NOT NULL COMMENT '被邀请人ID',
    reward DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '分润金额',
    reward_type VARCHAR(32) NOT NULL DEFAULT 'earnings' COMMENT '奖励类型: earnings-收益分成',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    INDEX idx_inviter_id (inviter_id),
    INDEX idx_invitee_id (invitee_id),
    INDEX idx_create_time (create_time),
    INDEX idx_inviter_type_reward (inviter_id, reward_type, reward),
    INDEX idx_inviter_type_create_time (inviter_id, reward_type, create_time),
    INDEX idx_inviter_invitee_reward (inviter_id, invitee_id, reward)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请收益分成记录表';

-- 3. 添加邀请相关的系统配置（收益分成比例，以后使用）
INSERT INTO system_config (config_key, config_value, remark, create_time) VALUES
('invite.earningsRate', '0.10', '邀请人收益分成比例(如0.10表示10%)', NOW())
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- =============================================
-- 用户反馈功能数据库结构
-- =============================================

-- 4. 创建用户反馈表
CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NULL COMMENT '反馈用户ID',
    type VARCHAR(32) NOT NULL COMMENT '反馈类型: suggestion-功能建议, bug-问题反馈, complaint-投诉建议, other-其他',
    content TEXT NOT NULL COMMENT '反馈内容',
    contact VARCHAR(100) NULL COMMENT '联系方式',
    images TEXT NULL COMMENT '截图URL，多个用逗号分隔',
    status TINYINT DEFAULT 0 COMMENT '处理状态: 0-待处理, 1-处理中, 2-已处理',
    reply TEXT NULL COMMENT '回复内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';
