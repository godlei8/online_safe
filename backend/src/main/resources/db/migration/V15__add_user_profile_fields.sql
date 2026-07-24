-- 个人中心：头像 URL、用户名最近修改时间。可重复执行。
SET @online_safe_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'avatar_url'
);

SET @online_safe_ddl := IF(
    @online_safe_column_exists = 0,
    'ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(512) NULL COMMENT ''头像公网 URL'' AFTER last_login_at',
    'SELECT 1'
);

PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'username_changed_at'
);

SET @online_safe_ddl := IF(
    @online_safe_column_exists = 0,
    'ALTER TABLE app_user ADD COLUMN username_changed_at TIMESTAMP(6) NULL COMMENT ''最近一次修改用户名时间'' AFTER avatar_url',
    'SELECT 1'
);

PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
