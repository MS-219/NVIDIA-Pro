-- Earnings and invite reward query performance indexes.
-- These indexes target frequent SUM/list queries used by app earnings pages.

SET @schema_name = DATABASE();

SET @idx_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'invite_reward'
    AND INDEX_NAME = 'idx_inviter_type_reward'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE invite_reward ADD INDEX idx_inviter_type_reward (inviter_id, reward_type, reward)',
  'SELECT ''idx_inviter_type_reward already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'invite_reward'
    AND INDEX_NAME = 'idx_inviter_type_create_time'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE invite_reward ADD INDEX idx_inviter_type_create_time (inviter_id, reward_type, create_time)',
  'SELECT ''idx_inviter_type_create_time already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'invite_reward'
    AND INDEX_NAME = 'idx_inviter_invitee_reward'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE invite_reward ADD INDEX idx_inviter_invitee_reward (inviter_id, invitee_id, reward)',
  'SELECT ''idx_inviter_invitee_reward already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'device_earnings'
    AND INDEX_NAME = 'idx_user_date_amount'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE device_earnings ADD INDEX idx_user_date_amount (user_id, date, amount)',
  'SELECT ''idx_user_date_amount already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'device_earnings'
    AND INDEX_NAME = 'idx_device_date_amount'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE device_earnings ADD INDEX idx_device_date_amount (device_id, date, amount)',
  'SELECT ''idx_device_date_amount already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'device_earnings'
    AND INDEX_NAME = 'idx_date_amount'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE device_earnings ADD INDEX idx_date_amount (date, amount)',
  'SELECT ''idx_date_amount already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
