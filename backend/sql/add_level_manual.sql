-- 添加 level_manual 字段，用于标记用户等级是否由后台手动设置
-- 手动设置的等级优先级高于系统自动升级

ALTER TABLE app_user ADD COLUMN level_manual TINYINT(1) DEFAULT 0 COMMENT '等级是否手动设置(1=是,0=否)';
