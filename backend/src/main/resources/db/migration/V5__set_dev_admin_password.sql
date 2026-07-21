-- 更新本地开发种子管理员口令的 BCrypt 哈希。
-- 仅更新初始种子账号；生产环境部署后应立即修改密码。
UPDATE admin_user
SET
    password_hash = '{bcrypt}$2a$10$dkNFyx/W/UcBpMetNSdwkeaVItqVjUDxQaPJ3BJ6QHoTdSFh1it2G',
    updated_at = UTC_TIMESTAMP(6)
WHERE id = '00000000-0000-4000-8000-000000000001'
  AND normalized_username = 'admin';
