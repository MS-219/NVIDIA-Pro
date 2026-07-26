-- AI 创作任务表
CREATE TABLE IF NOT EXISTS `ai_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` VARCHAR(64) NOT NULL COMMENT '任务ID (UUID)',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型: text-to-video, image-to-video, text-to-image, image-to-image',
    `prompt` TEXT COMMENT '提示词/描述',
    `input_image_url` VARCHAR(512) COMMENT '输入图片URL',
    `options` VARCHAR(256) COMMENT '选项 (时长/尺寸等)',
    `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '状态: pending, processing, completed, failed',
    `result_url` VARCHAR(512) COMMENT '结果URL',
    `error_msg` VARCHAR(512) COMMENT '错误信息',
    `cost_quota` INT DEFAULT 0 COMMENT '消耗配额',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `complete_time` DATETIME COMMENT '完成时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI创作任务表';

-- 为 app_user 表添加配额字段
-- 注意: 如果已存在 quota 列会报错，可忽略
-- ALTER TABLE `app_user` ADD COLUMN `quota` INT DEFAULT 100 COMMENT '剩余配额';

-- 如果需要添加 quota 字段，请先检查是否存在，然后手动执行：
-- 检查: SHOW COLUMNS FROM `app_user` LIKE 'quota';
-- 如果不存在则执行: ALTER TABLE `app_user` ADD COLUMN `quota` INT DEFAULT 100 COMMENT '剩余配额';
