-- 幂等：建表 IF NOT EXISTS + 条件建索引
CREATE TABLE IF NOT EXISTS announcement (
    id CHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    starts_at DATETIME(6) NULL,
    ends_at DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    created_by_admin_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_announcement PRIMARY KEY (id),
    CONSTRAINT fk_announcement_admin FOREIGN KEY (created_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT ck_announcement_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE')),
    CONSTRAINT ck_announcement_pinned CHECK (pinned IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'announcement' AND INDEX_NAME = 'idx_announcement_status_published'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_announcement_status_published ON announcement (status, published_at)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'announcement' AND INDEX_NAME = 'idx_announcement_pinned_published'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_announcement_pinned_published ON announcement (pinned, published_at)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;

CREATE TABLE IF NOT EXISTS announcement_read (
    user_id CHAR(36) NOT NULL,
    announcement_id CHAR(36) NOT NULL,
    read_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_announcement_read PRIMARY KEY (user_id, announcement_id),
    CONSTRAINT fk_announcement_read_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_announcement_read_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @online_safe_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'announcement_read' AND INDEX_NAME = 'idx_announcement_read_announcement'
);
SET @online_safe_ddl := IF(
    @online_safe_idx = 0,
    'CREATE INDEX idx_announcement_read_announcement ON announcement_read (announcement_id)',
    'SELECT 1'
);
PREPARE online_safe_stmt FROM @online_safe_ddl;
EXECUTE online_safe_stmt;
DEALLOCATE PREPARE online_safe_stmt;
