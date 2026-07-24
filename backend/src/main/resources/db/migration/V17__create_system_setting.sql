-- 系统设置覆盖表；可重复执行
CREATE TABLE IF NOT EXISTS system_setting (
    setting_key VARCHAR(96) NOT NULL PRIMARY KEY,
    value_json JSON NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    updated_by_admin_id CHAR(36) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
