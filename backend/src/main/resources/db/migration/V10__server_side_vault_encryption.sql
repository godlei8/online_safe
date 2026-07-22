-- 服务端加密保险箱：清空旧客户端密文，删除 key_bundle，nonce 改为 AES-GCM 12 字节，增加 key_id
DELETE FROM vault_item;
DELETE FROM private_template;
DROP TABLE IF EXISTS vault_key_bundle;

ALTER TABLE vault_item
    MODIFY COLUMN nonce BINARY(12) NOT NULL;

ALTER TABLE private_template
    MODIFY COLUMN nonce BINARY(12) NOT NULL;

SET @online_safe_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vault_item' AND COLUMN_NAME = 'key_id'
);
SET @online_safe_ddl := IF(
    @online_safe_col = 0,
    'ALTER TABLE vault_item ADD COLUMN key_id SMALLINT NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'private_template' AND COLUMN_NAME = 'key_id'
);
SET @online_safe_ddl := IF(
    @online_safe_col = 0,
    'ALTER TABLE private_template ADD COLUMN key_id SMALLINT NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
