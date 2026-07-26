-- 银行卡信息字段
ALTER TABLE app_user ADD COLUMN bank_name VARCHAR(50) COMMENT '银行名称';
ALTER TABLE app_user ADD COLUMN bank_card_no VARCHAR(50) COMMENT '银行卡号';
ALTER TABLE app_user ADD COLUMN bank_holder_name VARCHAR(50) COMMENT '持卡人姓名';
