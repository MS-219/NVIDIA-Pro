-- 为 app_user 表添加后台备注字段
ALTER TABLE app_user
    ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '后台备注';
