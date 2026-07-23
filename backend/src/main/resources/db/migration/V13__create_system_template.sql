-- 系统模板：明文结构，无真实账密
CREATE TABLE IF NOT EXISTS system_template (
    id CHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    platform VARCHAR(128) NOT NULL,
    channel VARCHAR(128) NOT NULL DEFAULT '',
    channel_url VARCHAR(512) NOT NULL DEFAULT '',
    payload_json LONGTEXT NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by_admin_id CHAR(36) NOT NULL,
    updated_by_admin_id CHAR(36) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_system_template PRIMARY KEY (id),
    CONSTRAINT fk_system_template_created_admin FOREIGN KEY (created_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT fk_system_template_updated_admin FOREIGN KEY (updated_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT ck_system_template_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_template' AND INDEX_NAME = 'idx_system_template_status_sort'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_system_template_status_sort ON system_template (status, sort_order)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_template' AND INDEX_NAME = 'idx_system_template_platform'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_system_template_platform ON system_template (platform)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
