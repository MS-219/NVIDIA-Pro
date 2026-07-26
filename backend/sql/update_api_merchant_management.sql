-- 如果 balance 已存在，请只执行下面这两行
ALTER TABLE `api_merchant` 
ADD COLUMN `level` int DEFAULT '0' COMMENT '商户等级',
ADD COLUMN `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话';
