# CURSOR.md — Online Safe 项目上下文

> 给 Cursor Agent 的项目速览。细则以产品需求、技术选型与 `AGENTS.md` 为准；本文只保留上手所需的事实与约束。

## 1. 产品是什么

**Online Safe（在线账密保险箱）**：多人独立使用、浏览器端加密的账密管理网站。

- 个人用户各自注册登录，只能管理自己的数据。
- 账密在浏览器内加密；服务端与管理员只能接触密文，不能读明文。
- 支持动态字段、系统/私人模板与模板快照；第一版以手动录入为主。
- 个人入口与管理员入口分离，后端做真实角色隔离。
- **登录成功即可使用保险箱**（无独立「解锁 / 锁定」产品概念）；密钥用登录密码在客户端包装。

仓库目录名是 `online-word`，产品与代码标识为 **online-safe** / `com.godlei.onlinesafe`。

## 2. 技术栈（已落地基线）

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21 默认构建（可 `-Djava.version=25`）、Spring Boot 4.1、Spring Security、Spring Session JDBC、Spring Data JPA、Flyway |
| 数据库 | MySQL 8.4 LTS（开发直连本机；测试用 H2） |
| 前端 | Vue 3、TypeScript 5.9、Vite、Vuetify 4、Vue Router、Pinia、Zod |
| 客户端加密 | libsodium.js（Argon2id + XChaCha20-Poly1305）；DEK 存 Pinia 内存，并在标签页 `sessionStorage` 暂存以便刷新恢复；禁止 localStorage |
| 认证模型 | Session Cookie（`ONLINE_SAFE_SESSION`）+ SPA CSRF；不用浏览器长期 JWT |
| 部署目标 | 同源部署（Caddy 静态资源 + `/api/*` 反代）；开发环境不用 Docker |

根 `pom.xml` 只聚合 `backend`；前端在 `frontend/` 用 npm，不进 Maven。

## 3. 目录地图

```text
online-word/
├─ AGENTS.md                          # 协作硬约束（中文优先）
├─ CURSOR.md                          # 本文件
├─ README.md
├─ docs/
│  ├─ Design.md                       # UI/交互规范（含统一二次确认弹窗）
│  ├─ project_notes/                  # 决策与关键事实（decisions / key_facts）
│  ├─ 在线账密保险箱-产品需求文档-v1.0.md
│  ├─ 在线账密保险箱-技术选型与架构设计-v1.0.md
│  └─ superpowers/specs/              # 专题设计（含密保与登录即开信封）
├─ pom.xml                            # Maven 聚合（仅 backend）
├─ backend/                           # Spring Boot
│  ├─ src/main/java/com/godlei/onlinesafe/
│  │  ├─ auth/        # 注册、登录、密保、忘记密码、会话、CSRF
│  │  ├─ admin/       # 管理端
│  │  ├─ vault/       # 密钥信封、密文记录、私人模板 API
│  │  ├─ security/    # SecurityFilterChain、Principal
│  │  └─ common/      # 统一错误模型
│  └─ src/main/resources/db/migration/   # Flyway V1–V9（幂等 SQL）
└─ frontend/
   ├─ src/
   │  ├─ api/         # fetch + CSRF + vault + auth
   │  ├─ crypto/      # libsodium 封装（登录密码 KEK）
   │  ├─ views/       # login / register / forgot-password / vault* / admin*
   │  ├─ stores/      # Pinia 会话与 DEK（标签页 sessionStorage 暂存；禁止 localStorage）
   │  ├─ components/  # AuthShell 等
   │  └─ plugins/     # Vuetify
   └─ design-reference/   # Stitch 设计稿截图与元数据
```

业务包内分层习惯：`web` → `application` → `domain` / `infrastructure`。Controller 不直接碰 Repository。

## 4. 当前进度（写代码前先看）

### 已完成

- 后端：注册（手机号 + 用户名 + 密码 + 确认密码 + **密保 1–3 题** + 数据库邀请码）、用户名/手机号登录、密保忘记密码、Session、CSRF、角色隔离、Flyway、初始管理员种子。
- 后端：管理员独立登录、邀请码与用户管理。
- 前端：个人登录/注册（含密保）/忘记密码、保险箱初始化/列表/详情/编辑/私人模板、登录后自动打开信封、刷新后自 `sessionStorage` 恢复 DEK；管理员登录与后台壳。
- 保险箱核心：客户端 libsodium 信封加密（**登录密码**包装 DEK）、key-bundle 初始化/读取、密文 items/private-templates API；密保重置登录密码时清除保险箱数据并重新初始化。
- **已删除**用户可见的「解锁保险箱 / 锁定保险箱 / 主密码 / 恢复密钥重包装」流程。

### 尚未实现（不要假装已有）

- 系统模板全量、TOTP UI、备份导出/恢复、回收站 UI、粘贴识别。
- 短信/邮箱重置、注册限流完善、管理端密文存储统计真值。
- 管理员 TOTP、安全日志业务、生产 Docker Compose/Caddy、前端 Vitest/Playwright 体系。

## 5. Agent 硬约束

1. **中文优先**：见 `AGENTS.md`。
2. **零知识边界**：不得把账密明文、登录密码、TOTP 种子送到服务端、日志或管理员接口。
3. **密码域**：网站登录密码（兼客户端 KEK）；密保答案只哈希，不参与 DEK。密保重置后清空保险箱并重新初始化，不提供恢复密钥重包装。
4. **无解锁产品概念**：登录成功即可看明文；勿恢复「主密码解锁 / 锁定 / 恢复密钥重包装」文案与路由门禁。
5. **Session + CSRF**：所有写请求先 `GET /api/csrf`；登录/退出后刷新 CSRF。
6. **角色隔离**：`/api/v1/**` → `ROLE_USER`；`/api/admin/**` → `ROLE_ADMIN`。
7. **数据迁移**：结构变更只走 Flyway；JPA `ddl-auto=validate`。
8. **密钥与本地配置**：勿提交 `application-local.yml`、真实密钥。
9. **UI**：遵循 `docs/Design.md`；主色 `#155EEF`。
10. **二次确认弹窗**：删除、标记异常、禁用用户等一律用 `OsConfirmDialog`（`os-confirm-dialog*` 色彩与动效）；禁止 `window.confirm` 与临时白框。详见 `docs/Design.md` §5.3.1 与 `docs/project_notes/decisions.md`。
11. **按钮高度**：相对旧规范整体 −10px——工作区默认 **30px**、认证主按钮 **38px**、确认弹窗按钮 **28px**；令牌见 `--os-control-*` / `docs/Design.md` §3.3。禁止再使用偏高的 40/48 默认。
12. **下拉框**：字段约 **34px**、选项行高 **32px**（`os-select-menu` / `--os-select-item`）；`vuetify.ts` 已默认挂 `menuProps.contentClass`。禁止偏高默认列表项。见 `docs/Design.md` §3.4。
13. **表单校验提示**：弹窗内校验/保存失败用 `useOsToast().error(...)` 独立弹出；禁止弹窗顶部红色 `v-alert` 横幅。

## 6. 关键 API（现状）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/csrf` | SPA CSRF Token |
| POST | `/api/auth/register` | 个人用户注册（含密保） |
| POST | `/api/auth/login` | 用户名或手机号登录 |
| POST | `/api/auth/logout` | 退出 |
| GET | `/api/auth/session` | 当前个人用户会话 |
| GET | `/api/auth/security-questions/builtins` | 内置密保题 |
| POST | `/api/auth/password-reset/lookup` | 忘记密码：取回密保问题 |
| POST | `/api/auth/password-reset/confirm` | 忘记密码：校验并设新密码；同时清除保险箱密文 |
| GET/PUT | `/api/v1/vault/key-bundle` | 密钥信封获取/初始化（PATCH 保留兼容，产品不再调用） |
| GET/POST/PUT/DELETE | `/api/v1/vault/items*` | 密文记录 CRUD |
| GET/POST/PUT/DELETE | `/api/v1/vault/private-templates*` | 私人模板 CRUD |
| POST | `/api/admin/auth/login` | 管理员登录 |
| GET | `/api/admin/auth/session` | 管理员会话 |
| GET/POST | `/api/admin/v1/invitations*` | 邀请码管理 |
| GET | `/actuator/health` | 健康检查 |

## 7. 本地命令

```powershell
Set-Location D:\Ai\online-word\backend
.\mvnw.cmd test
# 或：.\mvnw.cmd "-Djava.version=21" test

.\mvnw.cmd spring-boot:run

Set-Location D:\Ai\online-word\frontend
npm install
npm run dev
npm run build
```

PowerShell 传递带点号的 Maven `-D` 参数时必须整体加引号。

## 8. 踩坑清单（已验证）

- **Spring Boot 4.1 / Jackson 3**：`ObjectMapper` 使用 `tools.jackson.databind.ObjectMapper`。
- **Java 版本**：POM 默认 `java.version=21`。
- **Flyway / MySQL**：DDL 非事务；迁移脚本须幂等。
- **TypeScript**：前端钉在 5.9.x。
- **Pinia**：可存会话与内存 DEK；标签页内可用 `sessionStorage` 暂存 DEK 以便刷新恢复；**禁止** `localStorage` 持久化 DEK/密码/明文。

## 9. 前端路由（现状）

| 路径 | 用途 |
| --- | --- |
| `/login` | 个人登录 |
| `/register` | 个人注册（三步：账号 / 密码 / 密保） |
| `/forgot-password` | 密保重置登录密码 |
| `/vault` | 保险箱首页（登录后直接可用） |
| `/vault/setup` | 首次初始化信封 |
| `/vault/rewrap` | 重定向到 `/vault`（已废弃） |
| `/vault/unlock` | 重定向到 `/vault`（已废弃） |
| `/vault/new` `/vault/items/:id` … | 新建、详情、编辑 |
| `/vault/templates` | 私人模板 |
| `/admin/*` | 管理端 |

## 10. 延伸阅读顺序

1. `AGENTS.md`
2. `docs/在线账密保险箱-产品需求文档-v1.0.md`
3. `docs/在线账密保险箱-技术选型与架构设计-v1.0.md`
4. `docs/superpowers/specs/2026-07-21-auth-security-questions-and-login-unlock-design.md`
5. `docs/superpowers/specs/2026-07-21-vault-core-design.md`
6. `docs/Design.md`
