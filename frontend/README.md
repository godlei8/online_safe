# Online Safe frontend

Vue 3 + TypeScript + Vite + Vuetify 实现个人认证与管理后台入口。

## Run locally

1. 按 [`../backend/README.md`](../backend/README.md) 配置 MySQL 并启动后端（`http://localhost:8080`）。
2. 在本目录执行 `npm install`。
3. 执行 `npm run dev`。
4. 打开 Vite 地址，通常为 `http://localhost:5173`。

Vite 将 `/api` 代理到 Spring Boot，开发期可保留 Session Cookie 与 CSRF 行为。

## Routes

- `/login`：个人用户登录（用户名或手机号）。
- `/register`：个人用户注册（需有效数据库邀请码）。
- `/vault`：登录后交接页；保险箱能力尚未实现。
- `/admin/login`：管理员登录（与个人入口分离）。
- `/admin`：管理后台总览壳。
- `/admin/invitations`：邀请码管理（创建 / 列表 / 停用）。
- `/admin/users`：用户管理（列表、统计、启用/禁用、使会话失效）。
- `/admin/templates`、`/admin/security-logs`、`/admin/settings`：占位页。

本地默认管理员见后端 README。前端在变更类请求前调用 `GET /api/csrf`，并携带 Cookie。
