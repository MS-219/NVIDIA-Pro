-- 为 app_user 表添加用户类型字段
-- personal=个人用户, company=公司用户
ALTER TABLE app_user
    ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'personal' COMMENT '用户类型: personal-个人用户, company-公司用户';

UPDATE app_user
SET user_type = 'personal'
WHERE user_type IS NULL OR user_type = '';
