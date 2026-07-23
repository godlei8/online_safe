-- 邀请码用途类型（如用户注册）；可重复执行
SET @online_safe_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'registration_invite'
      AND COLUMN_NAME = 'purpose'
);

SET @online_safe_ddl := IF(
    @online_safe_column_exists = 0,
    'ALTER TABLE registration_invite ADD COLUMN purpose VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''USER_REGISTRATION'' AFTER status',
    'SELECT 1'
);

PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

UPDATE registration_invite
SET purpose = 'USER_REGISTRATION'
WHERE purpose IS NULL OR purpose = '';
