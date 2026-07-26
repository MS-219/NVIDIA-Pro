-- 给已绑定但没有业务号的设备补充业务号
-- 业务号格式: YW + 时间戳

UPDATE device 
SET business_id = CONCAT('YW', UNIX_TIMESTAMP() * 1000 + id)
WHERE user_id IS NOT NULL 
  AND (business_id IS NULL OR business_id = '');
