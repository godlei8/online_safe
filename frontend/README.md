# Online Safe 前端

Vue 3 + TypeScript + Vite + Vuetify 4 + Pinia + Vue Router + Zod。个人认证、保险箱与管理后台 SPA；开发期通过 Vite 将 `/api` 代理到 Spring Boot，保留 Session Cookie 与 CSRF。

保险箱数据经 API **明文读写**，由服务端 AES-GCM 加密入库；前端无客户端加密模块，也无解锁 / DEK 流程。

## 本地运行

1. 按 [`../backend/README.md`](../backend/README.md) 配置 MySQL 并启动后端（`http://localhost:8080`）。
2. 在本目录执行：

```powershell
npm install
npm run dev
```

3. 打开 Vite 地址（通常 `http://localhost:5173`）。

生产构建：`npm run build`（产物在 `dist/`）。类型检查：`npx vue-tsc --noEmit -p tsconfig.app.json`。

本地默认管理员见后端 README。前端在变更类请求前调用 `GET /api/csrf`，并携带 Cookie。

## 路由

| 路径 | 说明 |
|---|---|
| `/login` | 个人登录（用户名或手机号） |
| `/register` | 两步注册：手机号短信验证 → 用户名与密码（**无邀请码**） |
| `/forgot-password` | 短信验证码重置密码 |
| `/vault` | 保险箱首页：列表/卡片、筛选搜索、新建记录 |
| `/vault/items/:id` | 记录详情：复制、网址跳转、手机号拨打、编辑入口 |
| `/vault/templates` | 系统模板（只读选用）+ 私人模板 CRUD |
| `/admin/login` | 管理员登录（与个人入口分离） |
| `/admin` | 管理后台总览 |
| `/admin/invitations` | 邀请码管理（创建 / 列表 / 复制明文 / 删除） |
| `/admin/users` | 用户管理（列表、统计、启用/禁用、吊销会话） |
| `/admin/announcements` | 系统公告（创建 / 编辑 / 发布 / 下线） |
| `/admin/templates` | 系统模板（创建 / 编辑 / 发布 / 下线 / 排序） |
| `/admin/security-logs`、`/admin/settings` | 占位页 |

历史路由 `/vault/setup`、`/vault/unlock`、`/vault/rewrap` 重定向至 `/vault`（旧客户端加密流程已废弃）。

## 功能概要

### 认证

- 注册：`REGISTER` 用途短信 → 设用户名密码；成功后手机号视为已验证。
- 重置密码：`RESET_PASSWORD` 用途短信 → 新密码；仅吊销会话，不清空保险箱。
- 密保题流程已下线，前端不再提供。

### 保险箱

- 记录：名称、平台、渠道名、渠道网址、有效期（空=永久）、备注、状态（正常 / 异常 / 过期）。
- 固定字段：账号、密码（不可删）；账号类型可为文本 / 邮箱 / **手机号**。
- 动态字段类型：文本、密码、邮箱、网址、**手机号**；支持敏感 / 可复制、排序与删除。
- 列表：卡片或列表视图；按平台 / 渠道 / 状态筛选；搜索名称、平台、渠道、账号等。
- 交互：一键复制、密码掩码与临时明文、详情页 `tel:` / 外链跳转。
- 私人模板：保存字段结构，一键生成新记录草稿。
- 系统模板：浏览已发布模板并选用创建；快照 `templateSnapshot.source=SYSTEM`。
- 布局内嵌新建 / 编辑弹窗（紧凑分区、类型色条、细滚动条）。

### 公告

- 顶栏铃铛：未读最新公告可强制确认；收件箱列表与已读。

### 管理端

- 用户仅见元数据（手机号脱敏等），**不可见**保险箱明文。
- 邀请码后台能力保留，供运营发放；注册接口不再强制校验。

## 设计与样式

- 规范：[`docs/Design.md`](../docs/Design.md) v3.1「金融级信任感」；实现于 `src/styles/main.scss`、`src/plugins/vuetify.ts`。
- 主色信任蓝 `#155EEF`，标题深海军蓝 `#0A2540`，页面底 `#F6F9FC`。
- 输入框聚焦：单像素主色描边，**无**外圈淡蓝光晕；全局细滚动条（6px）。
- 记录表单：分区色标、字段类型色条、入场微动效；尊重 `prefers-reduced-motion`。

## 目录提示

| 路径 | 说明 |
|---|---|
| `src/views/` | 页面（登录、保险箱、管理端） |
| `src/components/vault/` | 记录表单与弹窗 |
| `src/domain/vaultPayload.ts` | 保险箱 payload / 字段类型 Zod 模型 |
| `src/api/`、`src/stores/` | HTTP 与 Pinia |
| `src/layouts/` | 保险箱 / 管理端壳层 |
