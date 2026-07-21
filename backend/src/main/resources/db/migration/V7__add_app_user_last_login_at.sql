-- 个人用户最近登录时间；登录成功时更新。可重复执行。
SET @online_safe_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'last_login_at'
);

SET @online_safe_ddl := IF(
    @online_safe_column_exists = 0,
    'ALTER TABLE app_user ADD COLUMN last_login_at DATETIME(6) NULL AFTER updated_at',
    'SELECT 1'
);

PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
