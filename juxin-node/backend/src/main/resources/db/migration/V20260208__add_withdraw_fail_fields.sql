-- 添加提现打款失败相关字段
-- 执行时间: 2026-02-08
-- 功能: 支持打款失败后自动回退并标注

-- 添加打款失败标记
ALTER TABLE `withdraw` ADD COLUMN `boss_kg_failed` TINYINT(1) DEFAULT 0 COMMENT '佣金保打款是否失败过(0-否 1-是)' AFTER `boss_kg_actual_amount`;

-- 添加打款失败次数
ALTER TABLE `withdraw` ADD COLUMN `payment_fail_count` INT DEFAULT 0 COMMENT '打款失败次数' AFTER `boss_kg_failed`;
