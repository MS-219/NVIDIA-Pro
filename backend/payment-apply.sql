CREATE TABLE `user_payment_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '申请人ID',
  `old_info` varchar(1000) DEFAULT NULL COMMENT '旧收款信息快照(JSON)',
  `payment_type` tinyint(2) NOT NULL DEFAULT 0 COMMENT '0-银行卡 1-支付宝',
  `new_card_no` varchar(100) NOT NULL COMMENT '新收款账号',
  `status` tinyint(2) NOT NULL DEFAULT 0 COMMENT '状态: 0待审核, 1已通过, 2已驳回',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '驳回理由',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款信息变更申请表';
