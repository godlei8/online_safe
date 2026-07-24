# 个人中心（头像 + 改用户名）设计规格

## 目标

用户从保险箱顶栏菜单进入「个人中心」，可：

1. 上传头像（后端中转至腾讯云 COS）
2. 修改登录用户名（格式同注册、全局唯一、30 天内仅可改一次）

本轮不做：改密码、换绑手机。

## 入口与页面

- `VaultLayout` 用户菜单：账户摘要 → **个人中心** → 退出登录
- 路由：`/vault/profile`（需登录且保险箱 ready）
- 顶栏头像：有 `avatarUrl` 显示图片，否则用户名首字母

## API（均需 ROLE_USER）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/profile` | 用户名、脱敏手机、头像 URL、下次可改用户名时间 |
| PATCH | `/api/v1/profile/username` | body `{ "username": "..." }` |
| POST | `/api/v1/profile/avatar` | multipart `file`，jpeg/png/webp，≤2MB |

Session（`/api/auth/session`、login）增加可选字段 `avatarUrl`。

## 用户名规则

- 格式：与注册相同 `^[\p{L}\p{N}_-]{3,32}$`
- 全局唯一（`normalized_username`，排除自己）
- 冷却：`username_changed_at` 起 30 天内不可再改；从未改过则可改
- 成功后更新 SecurityContext 中的 `AppUserPrincipal.username`

## 头像与 COS

- 配置：`COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_REGION`、`COS_BUCKET`、`COS_PATH_PREFIX`、`COS_PUBLIC_BASE_URL`（可选）
- 对象键：`{prefix}/{userId}/{uuid}.{ext}`
- 本地/测试：`app.cos.provider=local` 写本地目录并经由受控 URL 访问；生产 `tencent`
- 旧对象本轮不强制删除

## 数据

Flyway V15：`app_user.avatar_url`（VARCHAR 512 NULL）、`app_user.username_changed_at`（TIMESTAMP NULL）

## 错误码（中文 message）

- `USERNAME_FORMAT_INVALID` / `USERNAME_ALREADY_EXISTS` / `USERNAME_CHANGE_COOLDOWN`
- `AVATAR_INVALID` / `AVATAR_TOO_LARGE` / `COS_UPLOAD_FAILED` / `COS_NOT_CONFIGURED`
