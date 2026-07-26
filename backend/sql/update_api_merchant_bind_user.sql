ALTER TABLE `api_merchant`
ADD COLUMN `bind_user_id` bigint(20) DEFAULT NULL COMMENT '绑定的平台用户ID（资金账号）' AFTER `contact_phone`;

ALTER TABLE `api_merchant`
ADD INDEX `idx_bind_user_id` (`bind_user_id`);
