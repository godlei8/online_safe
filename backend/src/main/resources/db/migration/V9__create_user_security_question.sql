-- 用户密保问题（答案仅存哈希；不参与保险箱 DEK 解包）
-- 语义说明：vault_key_bundle.wrapped_dek_master* 列名保留，实际为「登录密码派生 KEK」包装的 DEK
CREATE TABLE IF NOT EXISTS user_security_question (
    id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    question_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    question_text VARCHAR(200) NOT NULL,
    answer_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_security_question PRIMARY KEY (id),
    CONSTRAINT fk_user_security_question_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_security_question_type CHECK (question_type IN ('BUILTIN', 'CUSTOM')),
    CONSTRAINT ck_user_security_question_sort CHECK (sort_order >= 0 AND sort_order <= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_security_question'
      AND INDEX_NAME = 'idx_user_security_question_owner_sort'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_user_security_question_owner_sort ON user_security_question (owner_id, sort_order)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
