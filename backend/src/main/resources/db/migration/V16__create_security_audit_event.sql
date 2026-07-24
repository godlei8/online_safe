-- 安全审计事件表；可重复执行
CREATE TABLE IF NOT EXISTS security_audit_event (
    id CHAR(36) NOT NULL PRIMARY KEY,
    occurred_at DATETIME(6) NOT NULL,
    category VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    result VARCHAR(16) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id CHAR(36) NULL,
    actor_label_snapshot VARCHAR(64) NULL,
    identifier_hint VARCHAR(64) NULL,
    identifier_hash CHAR(64) NULL,
    target_type VARCHAR(32) NULL,
    target_id VARCHAR(64) NULL,
    target_label_snapshot VARCHAR(128) NULL,
    error_code VARCHAR(64) NULL,
    request_id VARCHAR(64) NULL,
    route_template VARCHAR(128) NULL,
    http_method VARCHAR(8) NULL,
    ip_masked VARCHAR(64) NULL,
    ip_fingerprint CHAR(64) NULL,
    browser_family VARCHAR(32) NULL,
    os_family VARCHAR(32) NULL,
    device_type VARCHAR(16) NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    metadata_json JSON NULL,
    CONSTRAINT chk_security_audit_occurrence CHECK (occurrence_count >= 1)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET @online_safe_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_audit_event' AND INDEX_NAME = 'idx_sae_occurred_at'
);
SET @online_safe_ddl := IF(@online_safe_idx_exists = 0,
    'CREATE INDEX idx_sae_occurred_at ON security_audit_event (occurred_at DESC)', 'SELECT 1');
PREPARE online_safe_stmt FROM @online_safe_ddl; EXECUTE online_safe_stmt; DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_audit_event' AND INDEX_NAME = 'idx_sae_event_type_occurred'
);
SET @online_safe_ddl := IF(@online_safe_idx_exists = 0,
    'CREATE INDEX idx_sae_event_type_occurred ON security_audit_event (event_type, occurred_at DESC)', 'SELECT 1');
PREPARE online_safe_stmt FROM @online_safe_ddl; EXECUTE online_safe_stmt; DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_audit_event' AND INDEX_NAME = 'idx_sae_risk_occurred'
);
SET @online_safe_ddl := IF(@online_safe_idx_exists = 0,
    'CREATE INDEX idx_sae_risk_occurred ON security_audit_event (risk_level, occurred_at DESC)', 'SELECT 1');
PREPARE online_safe_stmt FROM @online_safe_ddl; EXECUTE online_safe_stmt; DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_audit_event' AND INDEX_NAME = 'idx_sae_result_occurred'
);
SET @online_safe_ddl := IF(@online_safe_idx_exists = 0,
    'CREATE INDEX idx_sae_result_occurred ON security_audit_event (result, occurred_at DESC)', 'SELECT 1');
PREPARE online_safe_stmt FROM @online_safe_ddl; EXECUTE online_safe_stmt; DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_audit_event' AND INDEX_NAME = 'idx_sae_actor_occurred'
);
SET @online_safe_ddl := IF(@online_safe_idx_exists = 0,
    'CREATE INDEX idx_sae_actor_occurred ON security_audit_event (actor_type, actor_id, occurred_at DESC)', 'SELECT 1');
PREPARE online_safe_stmt FROM @online_safe_ddl; EXECUTE online_safe_stmt; DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_audit_event' AND INDEX_NAME = 'idx_sae_identifier_hash'
);
SET @online_safe_ddl := IF(@online_safe_idx_exists = 0,
    'CREATE INDEX idx_sae_identifier_hash ON security_audit_event (identifier_hash, occurred_at DESC)', 'SELECT 1');
PREPARE online_safe_stmt FROM @online_safe_ddl; EXECUTE online_safe_stmt; DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_audit_event' AND INDEX_NAME = 'idx_sae_target_occurred'
);
SET @online_safe_ddl := IF(@online_safe_idx_exists = 0,
    'CREATE INDEX idx_sae_target_occurred ON security_audit_event (target_type, target_id, occurred_at DESC)', 'SELECT 1');
PREPARE online_safe_stmt FROM @online_safe_ddl; EXECUTE online_safe_stmt; DEALLOCATE PREPARE online_safe_stmt;
