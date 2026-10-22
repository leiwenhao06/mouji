-- ============================================================
-- 用户个人资料（头像/昵称）增量脚本（幂等，可重复执行）
-- 适用于已初始化过的数据库；全新安装执行 init.sql 即可。
-- 用法：mysql --default-character-set=utf8mb4 -u root -p < sql/user_profile.sql
-- ============================================================
USE eye_followup_ledger;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'avatar_path'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN avatar_path VARCHAR(500) DEFAULT NULL COMMENT ''头像文件路径'' AFTER display_name',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
