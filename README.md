# Online Safe（online_safe）

多人独立使用的在线账密保险箱：个人用户管理账密记录，管理员维护邀请码、用户与系统公告。个人入口与管理员入口分离，Session + CSRF，同源 SPA。

> **加密模型（现行）**：保险箱密文由**服务端 AES-GCM** 加密后入库；前端登录后以明文 `payload` 读写 API。已不再采用浏览器端零知识 / libsodium 方案。服务端持有 `VAULT_ENCRYPTION_KEY`，管理员 API **不会**返回用户保险箱明文，但运维侧具备密钥即具备解密能力。

## 仓库结构

| 路径 | 说明 |
|---|---|
| `backend/` | Spring Boot API、Flyway、集成测试 |
| `frontend/` | Vue 3 + Vuetify SPA |
| `deploy/` | Docker Compose + Caddy 生产部署 |
| `docs/` | 产品 / 设计 / 部署与专题规格 |
| `AGENTS.md` | 中文优先等协作约定 |

根目录 `pom.xml` 仅聚合后端 Maven 模块。IDE 可打开根 `pom.xml` 或 `backend/pom.xml`；前端仍用 npm。

## 当前进度（截至 2026-07）

### 已落地

- **认证**：手机号短信验证码注册；用户名/手机号登录；短信重置密码（吊销会话，保留保险箱）
- **保险箱**：记录 CRUD、私人模板、列表/卡片、筛选搜索、动态字段（含手机号类型）、渠道网址、有效期与状态
- **系统模板**：管理员维护明文字段结构（发布/下线/排序）；用户模板页可选用创建记录
- **加密**：服务端 AES-GCM；登录即可用，无解锁 / DEK / key-bundle 流程
- **公告**：管理员发布/下线；用户收件箱与未读强制确认
- **管理端**：邀请码、用户启停与吊销会话、公告与系统模板（邀请码后台仍可用，**注册不再强制邀请码**）
- **部署**：`deploy/` 下 Compose + Caddy HTTPS；支持本机构建产物的预构建镜像流程
- **前端体验**：金融信任蓝视觉、紧凑表单弹窗、细滚动条、输入框无外圈光晕

### 尚未实现 / 占位

- 管理员 TOTP、安全日志业务接口
- 管理端「安全日志 / 设置」占位页
- 系统模板「复制为私人模板」等增强（见专题规格后续项）
- 注册全局限流等运营加固项

## 本地快速开始

1. 按 [`backend/README.md`](./backend/README.md) 准备 MySQL 并启动后端（默认 `http://localhost:8080`）。
2. 按 [`frontend/README.md`](./frontend/README.md) 安装依赖并 `npm run dev`（默认 `http://localhost:5173`，`/api` 代理到后端）。
3. 本地短信默认打日志（`SMS_PROVIDER=logging`），无需阿里云即可走通注册 / 重置密码。

生产部署见 [`deploy/`](./deploy/) 与下方文档。

## 项目资料

| 文档 | 说明 |
|---|---|
| [界面与交互设计规范](./docs/Design.md) | **现行** UI 令牌与组件规范（v3.1） |
| [前端设计参考](./docs/DESIGN_REFERENCE.md) | 视觉参考 |
| [Docker 生产部署计划](./docs/Docker生产部署计划-v1.0.md) | 部署方案 |
| [Docker 生产部署实施记录](./docs/Docker生产部署实施记录-v1.0.md) | 实施记录 |
| [服务端加密设计](./docs/superpowers/specs/2026-07-22-server-side-vault-encryption-design.md) | 保险箱加密专题 |
| [短信验证设计](./docs/superpowers/specs/2026-07-22-sms-verification-auth-design.md) | 注册 / 重置密码短信专题 |
| [系统公告设计](./docs/superpowers/specs/2026-07-22-announcements-design.md) | 公告专题 |
| [产品需求文档](./docs/在线账密保险箱-产品需求文档-v1.0.md) | 早期 PRD（部分章节已过时，以代码与专题规格为准） |
| [技术选型与架构](./docs/在线账密保险箱-技术选型与架构设计-v1.0.md) | 早期架构（加密与密保等已变更） |
| [后端开发说明](./backend/README.md) | 环境、Flyway、接口 |
| [前端开发说明](./frontend/README.md) | 路由、功能、本地运行 |

## 协作约定

见 [`AGENTS.md`](./AGENTS.md)：业务文案、注释、提交说明与开发沟通**中文优先**。
