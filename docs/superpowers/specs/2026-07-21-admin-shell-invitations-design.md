# 管理员后台壳与邀请码设计

> 状态：已确认，进入实现
> 日期：2026-07-21

## 目标

完成本轮：管理员与个人登录区分、管理后台壳、邀请码管理（对齐 `stitch-admin-invitations.png`），注册改为校验数据库邀请码。本轮不做 TOTP。

## 认证

- 个人：`/api/auth/*` → `AppUserPrincipal` / `ROLE_USER`
- 管理员：`/api/admin/auth/login|logout|session` → `AdminUserPrincipal` / `ROLE_ADMIN`
- 独立 `AuthenticationManager`（或等价手写校验），只查 `admin_user`
- 种子账号 `admin`，默认口令见 README（本轮固定为可文档化口令并更新种子哈希）
- 同一 Session Cookie；后登录覆盖前会话

## 邀请码

表 `registration_invite` + `registration_invite_redemption`（见会话设计第 2 节）。

管理 API：

- `GET /api/admin/v1/invitations`（分页、状态/类型筛选、备注或创建者搜索）
- `GET /api/admin/v1/invitations/stats`（总数/可用/即将过期/已停用或耗尽）
- `POST /api/admin/v1/invitations`
- `POST /api/admin/v1/invitations/{id}/disable`

明文仅创建时返回一次；库内存哈希；注册事务内消耗。

## 前端

- `AdminLayout`：侧栏/移动端抽屉，对齐总览与邀请码原型
- `/admin/login`、`/admin`、`/admin/invitations` 可用
- 其余管理路由占位
- 邀请码页：统计卡、搜索筛选、表格、创建对话框、停用

## 非目标

TOTP、用户管理/模板/日志业务、开放注册开关。
