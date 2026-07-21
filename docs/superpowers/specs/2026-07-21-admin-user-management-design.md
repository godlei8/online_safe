# 管理员用户管理设计

> 状态：已确认，进入实现
> 日期：2026-07-21
> 原型：`frontend/design-reference/stitch-admin-users.png`

## 目标

管理后台 `/admin/users`：查询个人用户、统计、启用/禁用、使会话失效。对齐桌面原型；不展示任何账密明文。

## 数据

- `app_user` 新增 `last_login_at DATETIME(6) NULL`；个人登录成功时更新。
- 密文存储量、保险箱记录数本轮占位（接口返回 `null`，前端显示「—」）。
- 活跃会话数、使会话失效：基于 `SPRING_SESSION.PRINCIPAL_NAME`（个人用户名为 `AppUser.username`）。

## API（`ROLE_ADMIN`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/v1/users` | 分页；`q`（手机号/用户名）、`status`、`registeredWithin`（`7d`/`30d`/`90d`） |
| GET | `/api/admin/v1/users/stats` | `total` / `active` / `disabled` / `activeLast7Days` |
| POST | `/api/admin/v1/users/{id}/disable` | 禁用并失效会话 |
| POST | `/api/admin/v1/users/{id}/enable` | 启用 |
| POST | `/api/admin/v1/users/{id}/revoke-sessions` | 仅失效会话 |

列表字段：`id`、`username`、`maskedPhone`、`status`、`createdAt`、`lastLoginAt`、`activeSessionCount`、`cipherStorageBytes`（null）、`recordCount`（null）。

## 前端

- 替换占位页为用户管理页：统计卡、说明条、筛选、表格、分页、刷新与操作确认。
- 沿用现有 `AdminLayout` 与邀请码页视觉语言。

## 非本轮

保险箱存储/记录真值、安全日志落库、删除用户、TOTP。
