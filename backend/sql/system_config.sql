-- 创建系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键名',
  `config_value` text COMMENT '配置值',
  `description` varchar(255) DEFAULT NULL COMMENT '配置描述',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 插入默认配置
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('earnings.hourlyRate', '2.4', '每小时收益（元）'),
('earnings.hashratePerYuan', '100', '算力兑换比例（多少聚芯算力值=1元）'),
('earnings.minWithdraw', '10', '最低提现金额（元）'),
('earnings.withdrawFee', '1', '提现手续费（%）'),
('ai.imageGenCost', '10', '图片生成消耗聚芯算力值'),
('ai.imageToVideoCost', '100', '图生视频消耗聚芯算力值'),
('ai.videoGenCost', '200', '视频生成消耗聚芯算力值'),
('ai.chatCost', '1', 'AI对话消耗聚芯算力值'),
('device.heartbeatTimeout', '120', '心跳超时时间（秒）'),
('device.offlineThreshold', '120', '离线判定时间（秒）'),
('device.autoAssignBusiness', 'true', '自动分配业务号'),
('system.siteName', '聚芯算力', '系统名称'),
('system.contactEmail', '', '联系邮箱'),
('system.maintenanceMode', 'false', '维护模式')
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
