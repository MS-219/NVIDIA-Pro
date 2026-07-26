-- 用户回收站字段。后端启动时也会幂等检查并自动创建。
SET @schema_name = DATABASE();

SET @deleted_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'deleted'
);
SET @sql = IF(
  @deleted_column_exists = 0,
  'ALTER TABLE app_user ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0-正常, 1-回收站''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @deleted_at_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'deleted_at'
);
SET @sql = IF(
  @deleted_at_column_exists = 0,
  'ALTER TABLE app_user ADD COLUMN deleted_at DATETIME NULL COMMENT ''删除时间''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @recycle_index_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'app_user' AND INDEX_NAME = 'idx_app_user_deleted'
);
SET @sql = IF(
  @recycle_index_exists = 0,
  'ALTER TABLE app_user ADD INDEX idx_app_user_deleted (deleted, deleted_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
