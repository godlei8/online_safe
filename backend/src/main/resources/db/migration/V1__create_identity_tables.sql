-- 幂等：表已存在则跳过（应对 MySQL DDL 非事务导致的半成功迁移）
CREATE TABLE IF NOT EXISTS app_user (
    id CHAR(36) NOT NULL,
    phone VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    username VARCHAR(64) NOT NULL,
    normalized_username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_phone UNIQUE (phone),
    CONSTRAINT uk_app_user_normalized_username UNIQUE (normalized_username),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS admin_user (
    id CHAR(36) NOT NULL,
    username VARCHAR(64) NOT NULL,
    normalized_username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_admin_user PRIMARY KEY (id),
    CONSTRAINT uk_admin_user_normalized_username UNIQUE (normalized_username),
    CONSTRAINT ck_admin_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
