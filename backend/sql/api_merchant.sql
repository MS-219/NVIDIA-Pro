CREATE TABLE `api_merchant` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `merchant_name` varchar(100) NOT NULL COMMENT '商户名称',
  `app_id` varchar(50) NOT NULL COMMENT 'AppID',
  `app_secret` varchar(100) NOT NULL COMMENT 'AppSecret',
  `permissions` text COMMENT '可见功能权限,逗号分隔',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态 0:禁用 1:启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_id` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口商户表';
