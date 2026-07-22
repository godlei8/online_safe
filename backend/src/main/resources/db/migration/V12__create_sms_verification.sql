-- 短信验证码记录（仅存哈希；幂等建表）
CREATE TABLE IF NOT EXISTS sms_verification (
    id CHAR(36) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    purpose VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    send_count_window INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    client_ip VARCHAR(64) NULL,
    CONSTRAINT pk_sms_verification PRIMARY KEY (id),
    CONSTRAINT ck_sms_verification_purpose CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sms_verification' AND INDEX_NAME = 'idx_sms_verification_phone_purpose_created'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_sms_verification_phone_purpose_created ON sms_verification (phone, purpose, created_at)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
