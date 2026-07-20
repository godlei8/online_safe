-- 初始管理员仅用于首次部署。密码以 BCrypt 哈希保存，不保留明文；首次登录后应立即修改密码并启用 MFA。
INSERT INTO admin_user (
    id,
    username,
    normalized_username,
    password_hash,
    status,
    mfa_enabled,
    created_at,
    updated_at,
    version
)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'admin',
    'admin',
    '{bcrypt}$2a$10$DfBPO1zcjBJEUVHoFY8MjOvdulIcrmzVfb5EDk0yk3uTDT0cyQKwS',
    'ACTIVE',
    FALSE,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM admin_user
    WHERE normalized_username = 'admin'
);
