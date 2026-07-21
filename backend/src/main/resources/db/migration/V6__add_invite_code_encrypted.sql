-- 加密保存邀请码明文，供管理员复制；列表接口永不返回明文。
-- 可重复执行：列已存在时跳过，避免“DDL 已成功但 Flyway 记失败”后无法重启。
SET @online_safe_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'registration_invite'
      AND COLUMN_NAME = 'code_encrypted'
);

SET @online_safe_ddl := IF(
    @online_safe_column_exists = 0,
    'ALTER TABLE registration_invite ADD COLUMN code_encrypted VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER code_hint',
    'SELECT 1'
);

PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
