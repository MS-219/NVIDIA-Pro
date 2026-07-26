-- 为设备表增加商户归属字段
ALTER TABLE `device` ADD COLUMN `merchant_id` bigint DEFAULT NULL COMMENT '归属商户ID';
