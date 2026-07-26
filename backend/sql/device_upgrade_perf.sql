-- 设备升级管理性能索引：升级批次较大时降低心跳确认和计数查询压力
SET @schema_name = DATABASE();

SET @idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'device_upgrade_record'
      AND index_name = 'idx_record_active_lookup'
);
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE device_upgrade_record ADD INDEX idx_record_active_lookup (status, device_sn, target_version)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'device_upgrade_record'
      AND index_name = 'idx_record_batch_status'
);
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE device_upgrade_record ADD INDEX idx_record_batch_status (batch_id, status)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
