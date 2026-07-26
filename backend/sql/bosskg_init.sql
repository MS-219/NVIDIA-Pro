-- ============================================
-- 佣金保灵活用工平台集成 - 数据库初始化脚本
-- ============================================

-- 1. 为 app_user 表添加身份证号字段
ALTER TABLE app_user ADD COLUMN id_card VARCHAR(18) DEFAULT NULL COMMENT '身份证号' AFTER bank_holder_name;

-- 2. 为 app_user 表添加身份证照片字段
ALTER TABLE app_user ADD COLUMN id_card_front VARCHAR(500) DEFAULT NULL COMMENT '身份证人像面照片URL' AFTER id_card;
ALTER TABLE app_user ADD COLUMN id_card_back VARCHAR(500) DEFAULT NULL COMMENT '身份证国徽面照片URL' AFTER id_card_front;

-- 3. 创建用户签约记录表
CREATE TABLE IF NOT EXISTS user_contract (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名', 
    id_card VARCHAR(18) NOT NULL COMMENT '身份证号',
    mobile VARCHAR(11) NOT NULL COMMENT '手机号',
    bank_card_no VARCHAR(30) DEFAULT NULL COMMENT '银行卡号',
    alipay_account VARCHAR(50) DEFAULT NULL COMMENT '支付宝账号',
    status TINYINT DEFAULT 0 COMMENT '签约状态: 0-待签约 1-已签约 2-签约失败 3-签约中 5-已解约',
    provider_id VARCHAR(20) NOT NULL COMMENT '服务商ID',
    payment_type TINYINT DEFAULT 0 COMMENT '签约方式: 0-银行卡 1-支付宝 2-微信',
    contract_time DATETIME DEFAULT NULL COMMENT '签约成功时间',
    fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_provider (user_id, provider_id) COMMENT '用户+服务商唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签约记录表';

-- 4. 为 withdraw 表添加佣金保相关字段
ALTER TABLE withdraw ADD COLUMN bosskg_batch_id VARCHAR(32) DEFAULT NULL COMMENT '佣金保批次号' AFTER remark;
ALTER TABLE withdraw ADD COLUMN bosskg_order_no VARCHAR(32) DEFAULT NULL COMMENT '佣金保平台订单号' AFTER bosskg_batch_id;
ALTER TABLE withdraw ADD COLUMN bosskg_state TINYINT DEFAULT NULL COMMENT '佣金保交易状态: 1-付款中 3-成功 4-失败 6-待确认 7-已取消' AFTER bosskg_order_no;
ALTER TABLE withdraw ADD COLUMN bosskg_fee DECIMAL(10,2) DEFAULT NULL COMMENT '佣金保平台管理费(元)' AFTER bosskg_state;
ALTER TABLE withdraw ADD COLUMN bosskg_user_fee DECIMAL(10,2) DEFAULT NULL COMMENT '个人服务费/个税(元)' AFTER bosskg_fee;
ALTER TABLE withdraw ADD COLUMN bosskg_actual_amount DECIMAL(10,2) DEFAULT NULL COMMENT '佣金保实际到账金额(元)' AFTER bosskg_user_fee;
ALTER TABLE withdraw ADD COLUMN id_card VARCHAR(18) DEFAULT NULL COMMENT '身份证号' AFTER real_name;

-- 5. 添加索引
CREATE INDEX idx_user_contract_user_id ON user_contract(user_id);
CREATE INDEX idx_user_contract_status ON user_contract(status);
CREATE INDEX idx_withdraw_bosskg_batch ON withdraw(bosskg_batch_id);
CREATE INDEX idx_withdraw_bosskg_order ON withdraw(bosskg_order_no);
