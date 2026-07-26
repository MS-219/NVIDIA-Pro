CREATE TABLE `device_offline_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) NOT NULL COMMENT '设备ID',
  `sn` varchar(64) DEFAULT NULL COMMENT '设备SN码',
  `offline_time` datetime DEFAULT NULL COMMENT '离线时间',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最后心跳时间',
  `reason` varchar(255) DEFAULT NULL COMMENT '离线原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_sn` (`sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备离线记录表';
