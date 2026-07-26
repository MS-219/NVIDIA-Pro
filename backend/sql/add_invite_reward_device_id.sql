-- 给 invite_reward 表添加 device_id 字段
-- 用于记录分润来源的设备

ALTER TABLE invite_reward ADD COLUMN device_id BIGINT NULL COMMENT '来源设备ID（收益分成时记录产生收益的设备）';
