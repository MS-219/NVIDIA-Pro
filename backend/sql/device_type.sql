-- 添加设备类型字段，区分真实设备和虚拟设备
-- 执行时间: 2026-01-08
-- 说明: 0=真实设备(默认), 1=虚拟设备(始终在线)

ALTER TABLE device ADD COLUMN type TINYINT DEFAULT 0 COMMENT '设备类型: 0=真实设备 1=虚拟设备';
