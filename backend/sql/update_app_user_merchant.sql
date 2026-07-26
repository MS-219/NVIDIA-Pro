ALTER TABLE `app_user` ADD COLUMN `merchant_id` bigint(20) DEFAULT NULL COMMENT '所属商户ID';
ALTER TABLE `app_user` ADD COLUMN `external_user_id` varchar(100) DEFAULT NULL COMMENT '外部系统用户ID';
ALTER TABLE `app_user` ADD INDEX `idx_merchant_external` (`merchant_id`, `external_user_id`);
