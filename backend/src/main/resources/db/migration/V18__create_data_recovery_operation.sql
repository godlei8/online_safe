CREATE TABLE IF NOT EXISTS data_recovery_operation (
    id CHAR(36) NOT NULL PRIMARY KEY,
    owner_id CHAR(36) NULL,
    scope VARCHAR(16) NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    format_version INT NULL,
    source_backup_id CHAR(36) NULL,
    total_count INT NOT NULL DEFAULT 0,
    processed_count INT NOT NULL DEFAULT 0,
    created_count INT NOT NULL DEFAULT 0,
    restored_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_data_recovery_owner_type_created (owner_id, operation_type, created_at),
    INDEX idx_data_recovery_owner_status (owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS data_recovery_batch (
    operation_id CHAR(36) NOT NULL,
    batch_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    entry_count INT NOT NULL DEFAULT 0,
    created_count INT NOT NULL DEFAULT 0,
    restored_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (operation_id, batch_no),
    CONSTRAINT fk_data_recovery_batch_operation
        FOREIGN KEY (operation_id) REFERENCES data_recovery_operation (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
