-- 镜像授权绑定工厂账号：工厂账号只能查看自己授权码刷出来的设备
ALTER TABLE image_license
    ADD COLUMN factory_username VARCHAR(64) NULL COMMENT '绑定工厂账号' AFTER created_by;

CREATE INDEX idx_image_license_factory_username ON image_license (factory_username);
