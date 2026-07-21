-- 幂等：建表 IF NOT EXISTS + 条件建索引，可安全重跑
CREATE TABLE IF NOT EXISTS registration_invite (
    id CHAR(36) NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_hint VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    max_uses INT NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by_admin_id CHAR(36) NOT NULL,
    note VARCHAR(200) NULL,
    last_used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_registration_invite PRIMARY KEY (id),
    CONSTRAINT uk_registration_invite_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_registration_invite_admin FOREIGN KEY (created_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT ck_registration_invite_max_uses CHECK (max_uses >= 1),
    CONSTRAINT ck_registration_invite_used_count CHECK (used_count >= 0),
    CONSTRAINT ck_registration_invite_status CHECK (status IN ('ACTIVE', 'DISABLED', 'EXHAUSTED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'registration_invite' AND INDEX_NAME = 'idx_registration_invite_status'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_registration_invite_status ON registration_invite (status)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'registration_invite' AND INDEX_NAME = 'idx_registration_invite_created_at'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_registration_invite_created_at ON registration_invite (created_at)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

CREATE TABLE IF NOT EXISTS registration_invite_redemption (
    id CHAR(36) NOT NULL,
    invite_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    redeemed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_registration_invite_redemption PRIMARY KEY (id),
    CONSTRAINT uk_registration_invite_redemption_user UNIQUE (user_id),
    CONSTRAINT fk_registration_invite_redemption_invite FOREIGN KEY (invite_id) REFERENCES registration_invite (id),
    CONSTRAINT fk_registration_invite_redemption_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'registration_invite_redemption' AND INDEX_NAME = 'idx_registration_invite_redemption_invite'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_registration_invite_redemption_invite ON registration_invite_redemption (invite_id)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
