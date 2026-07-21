-- 保险箱密钥信封、密文记录、私人模板（纯密文，无业务明文列）
CREATE TABLE IF NOT EXISTS vault_key_bundle (
    owner_id CHAR(36) NOT NULL,
    kdf_salt VARBINARY(64) NOT NULL,
    kdf_ops_limit BIGINT NOT NULL,
    kdf_mem_limit BIGINT NOT NULL,
    wrapped_dek_master LONGBLOB NOT NULL,
    wrapped_dek_master_nonce BINARY(24) NOT NULL,
    wrapped_dek_recovery LONGBLOB NOT NULL,
    wrapped_dek_recovery_nonce BINARY(24) NOT NULL,
    algo_version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_vault_key_bundle PRIMARY KEY (owner_id),
    CONSTRAINT fk_vault_key_bundle_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT ck_vault_key_bundle_algo CHECK (algo_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS vault_item (
    id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    ciphertext LONGBLOB NOT NULL,
    nonce BINARY(24) NOT NULL,
    algo_version INT NOT NULL,
    payload_version INT NOT NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_vault_item PRIMARY KEY (id),
    CONSTRAINT fk_vault_item_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT ck_vault_item_algo CHECK (algo_version >= 1),
    CONSTRAINT ck_vault_item_payload CHECK (payload_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vault_item' AND INDEX_NAME = 'idx_vault_item_owner_updated'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_vault_item_owner_updated ON vault_item (owner_id, updated_at)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vault_item' AND INDEX_NAME = 'idx_vault_item_owner_deleted'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_vault_item_owner_deleted ON vault_item (owner_id, deleted_at)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

CREATE TABLE IF NOT EXISTS private_template (
    id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    ciphertext LONGBLOB NOT NULL,
    nonce BINARY(24) NOT NULL,
    algo_version INT NOT NULL,
    payload_version INT NOT NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_private_template PRIMARY KEY (id),
    CONSTRAINT fk_private_template_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT ck_private_template_algo CHECK (algo_version >= 1),
    CONSTRAINT ck_private_template_payload CHECK (payload_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'private_template' AND INDEX_NAME = 'idx_private_template_owner_updated'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_private_template_owner_updated ON private_template (owner_id, updated_at)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
