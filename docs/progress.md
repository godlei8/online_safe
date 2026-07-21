# Progress Log: 在线账密保险箱技术选型

## Session: 2026-07-20

### 后端搭建启动

- **Status:** in_progress
- 用户要求先搭建后端，新增 Phase 6 至 Phase 10。
- 当前范围为 Spring Boot 基础工程、注册认证、安全配置、MySQL 迁移、测试和启动说明；前端不在本轮范围。
- planning session-catchup 因本机 `python` 指向 Microsoft Store 别名而无法运行，改用现有规划文件和 Git 状态继续。
- 环境检查确认 Java 21.0.10、Maven 3.9.9 和 MySQL 8.4.8 可用，未发现 JDK 25 或 Docker。
- Docker 命令检查产生 CommandNotFoundException；不重复调用，后续使用本机构建和可选 MySQL 服务验证。
- 用户确认开发环境暂不使用 Docker，已将 Phase 7 和 Phase 9 调整为本机 MySQL + 内存测试库方案。
- Maven Wrapper 文件已成功生成；命令尾部未加引号的 `-Djava.version=21` 被 PowerShell 错误拆分，已记录并改用带引号参数。
- 首次 Maven 编译发现 Spring Boot 4.1 的 Jackson 3 包名变更，已修正 `ObjectMapper` 导入，准备重新测试。
- 使用 Java 21 兼容覆盖执行 Maven 测试成功：8项测试全部通过。
- 覆盖手机号规范化、注册唯一性、确认密码、CSRF、用户名/手机号登录和用户/管理员接口隔离。
- 清理 Spring Security 认证提供器自动装配告警，并将登录手机号识别的异常捕获范围收紧为格式异常。
- 复测通过：`mvnw.cmd "-Djava.version=21" test`，8项测试全部通过。
- 打包通过：`mvnw.cmd "-Djava.version=21" package -DskipTests`，生成 Spring Boot 可运行 jar。
- 执行 Git 空白检查、敏感值扫描和尾随空格检查；未发现真实凭据或格式问题。敏感扫描命中仅为 Maven Wrapper 的变量和 README/配置中的本机密码占位符。
- 本机 MySQL 服务未运行，未执行真实 MySQL 迁移；启动说明已写入 `../backend/README.md`。

### 注册需求调整

- **Status:** complete
- 将第一版个人用户注册字段调整为手机号、用户名、密码、确认密码。
- 删除 P0 中的邮箱注册和邮箱验证要求。
- 明确第一版不发送短信验证码，手机号记录为未验证状态。
- 补充手机号/用户名唯一约束、确认密码不落库和注册限流要求。
- 一次组合补丁因进度文件标题上下文不匹配而失败，拆分后已成功写入，未造成内容损坏。

### 技术选型文档交付

- **Status:** complete
- 完成 `在线账密保险箱-技术选型与架构设计-v1.0.md`。
- 确定 Java 25、Spring Boot 4.1、Spring Security、MySQL 8.4 LTS、Vue 3、TypeScript、Vite 和 Vuetify 4 技术基线。
- 写明认证会话、CSRF、管理员 MFA、浏览器端加密、多用户隔离、项目结构、测试与 Docker Compose 部署方案。
- 对文档执行标题结构和敏感值扫描，未发现真实凭据或密钥样式内容。
- 对照最新注册需求确认手机号、用户名、密码、确认密码及第一版无短信验证已覆盖。
- 一次规划文件组合补丁因 findings 上下文不匹配失败，拆分后已完成更新。
- 首次计划状态检查使用了不适用于 `rg` 的换行正则，已改为单行模式重新检查，最终无未完成项。

### Phase 1: 需求与约束提取

- **Status:** complete
- **Started:** 2026-07-20
- Actions taken:
  - 读取 planning-with-files 技能完整说明。
  - 检查项目中不存在旧的规划文件。
  - 检查记忆索引，没有发现与 online-word 或本项目直接相关的历史记录。
  - 创建本轮任务计划、研究记录和进度日志。
  - 完整复核产品需求文档，提取零知识加密、动态字段、模板快照、多租户隔离和多设备同步等约束。
- Files created/modified:
  - `task_plan.md`（创建）
  - `findings.md`（创建）
  - `progress.md`（创建）

### Phase 2: 官方资料调研

- **Status:** complete
- Actions taken:
  - 已明确需要调研的技术层级，准备核验官方资料。
  - 核验 Node.js 官方支持周期，确认 Node.js 24 为当前生产 LTS，Node.js 26 仍为 Current。
  - 核验 Vue 官方 TypeScript/Vite 建议和 Pinia 推荐地位。
  - 核验 PostgreSQL 18 支持状态和小版本策略。
  - 核验 Drizzle 对 PostgreSQL RLS 和迁移的支持。
  - 核验 Better Auth 的邮箱密码、验证、会话、管理员和 2FA 能力及其边界。
  - 核验 NestJS Fastify 适配器和 Better Auth NestJS 社区集成状态，发现 Fastify 桥接仍有 beta 风险。
  - 核验 Libsodium Argon2id 与 XChaCha20-Poly1305 官方建议。
  - 核验 W3C Web Crypto 原语范围、RFC 9106 和 RFC 6238。
  - 比较 Nuxt 全栈与 Vue SPA + 独立 API 的边界，记录 SSR 对本产品收益有限。
  - 核验 Hono Node.js、Zod 校验、RPC、CSRF 和安全响应头能力。
  - 核验 Better Auth 对 Hono 的直接集成、默认 scrypt、管理员权限及限流存储。
  - 核验 Playwright 跨浏览器能力和 Caddy 自动 HTTPS/反向代理能力。
  - 核验 PostgreSQL RLS 的绕过条件、默认拒绝行为、`bytea` 存储和备份方式。
  - 核验 Tailwind CSS、Reka UI 可访问性原语和 Docker Compose secrets/healthcheck。
  - 核验 Zod 4 稳定状态、Vitest Browser Mode、Nodemailer SMTP 和 Pino 日志脱敏。
  - 核验 WebAssembly 在 CSP 下需要 `wasm-unsafe-eval`，将其列为加密原型必测项。
- Files created/modified:
  - 无。

### Phase 3: 方案比较与决策

- **Status:** in_progress

### Phase 2B: 用户指定技术栈复核

- **Status:** in_progress
- Actions taken:
  - 接收用户指定的 Java、Spring Boot、MySQL 8、Vue 和 Vuetify 技术方向。
  - 初步决定采用 Spring Security，不使用自研轻量登录拦截器。
  - 将 Node/Hono 方案降为已否决备选，开始复核 Java 技术栈官方资料。
  - 核验 Spring Boot 4.1.0 对 Java 17-26 的支持，记录 Java 25 为当前 LTS。
  - 核验 MySQL 8.4 为 LTS 分支，决定将“MySQL 8”具体化为 8.4.x 最新补丁版。
  - 核验 Spring Security 7.1 的认证、授权、会话和 SPA CSRF 能力。
  - 核验 Spring Session JDBC 可将 HttpSession 存入 MySQL。
  - 核验 Vue 3 + TypeScript + Vite、Pinia 3 和 Vuetify 4 当前状态。
  - 核验 Spring Data JPA 与 Flyway MySQL 支持，记录 MySQL 8.4 集成需要原型验证。

### Phase 4: 技术选型文档

- **Status:** pending

### Phase 5: 校验与交付

- **Status:** pending

## Test Results

| Test | Input | Expected | Actual | Status |
| --- | --- | --- | --- | --- |
| 规划文件初始化 | 项目根目录 | 三个规划文件存在 | 已创建 | 通过 |
| Java 25 backend tests | Corretto 25.0.3 | 8 authentication tests pass | 8 passed | 通过 |
| Frontend production build | Vue + Vuetify frontend | vue-tsc and Vite build pass | `npm run build` passed | 通过 |
| MySQL initialization | local `online_safe` database | Flyway V1/V2 applied and identity/session tables exist | V1 and V2 successful | 通过 |

## Error Log

| Timestamp | Error | Attempt | Resolution |
| --- | --- | --- | --- |
| 2026-07-20 | 首次更新规划文件时补丁上下文匹配失败 | 1 | 拆分补丁并按当前结构重新应用 |
| 2026-07-20 | vue-tsc 3.3.7 with TypeScript 7 cannot load `typescript/lib/tsc` | 1 | Pinned TypeScript 5.9.3, added alias and Node type declarations, then built successfully |

## Session: 2026-07-20 — Homepage Stitch optimization

- **Status:** in_progress
- Read existing planning files and confirmed the current frontend and Stitch authentication references are already present.
- Ran small-repository reconnaissance: 2 commits, 4 branches, no bug-fix hotspot overlap, and no firefighting commits.
- Next: inspect `Design.md`, the product requirements, and the current homepage implementation before opening Stitch.

### Homepage Stitch optimization

- **Status:** in_progress
- Confirmed project `Online Security Vault` and source screen `4f0ecf185e974a07b7fb216362900c54`.
- 已下载来源截图至 `../frontend/design-reference/stitch-vault-dashboard-current.png` 用于对比。
- Edited the existing homepage concept in Stitch using the product requirements and `Design.md`; Stitch created optimized screen `3bc78f70199b4793b6184bee023880f8` rather than overwriting the source.
- 已下载优化截图至 `../frontend/design-reference/stitch-vault-dashboard-optimized.png`，并检查桌面端结果：中文导航与状态文案、搜索/筛选/排序行、更明确的新增记录操作、仅元数据卡片及敏感字段隐藏提示均已体现。
- Remaining: create or verify a mobile homepage variant and record the final Stitch IDs/implementation notes.
- Mobile variant `7717a41a92a34c4a91c1cf475bed0fd1` was generated and visually checked. Layout and first-screen priorities are correct, but bottom navigation still contains English labels; a targeted Stitch correction is pending.
- Applied the targeted mobile correction in Stitch. The returned project update event confirms bottom navigation labels changed to `保险箱 / 安全报告 / 生成器 / 设置` and the active tab styling was corrected. Immediate exported HTML/screenshot verification returned a cached pre-edit copy, which was recorded as a verification limitation rather than retried blindly.
- 已更新 `DESIGN_REFERENCE.md`，记录来源、优化后桌面端和优化后移动端的 Stitch 界面标识及本地参考资源。
- Homepage Stitch optimization phase is complete. No frontend production code was changed in this task; the local `VaultHomeView.vue` remains a placeholder for the next implementation phase.

### 管理员后台首页 Stitch 原型

- **Status:** in_progress
- Reopened the planning workflow for a new Phase 17 task.
- Next: read the admin sections of the requirements and inspect the existing Stitch project screens before generating the admin console.

- Read the admin role, page routes, management modules, security boundaries, and responsive requirements from the product document; read the relevant Design.md color, typography, spacing, component, and status rules.
- Confirmed no admin screen existed in Stitch before this task.
- 已在项目 `10594920934536828879` 中生成桌面端管理后台界面 `e81db235f42a4b13b88152960ae0f62a`，并保存参考图至 `../frontend/design-reference/stitch-admin-dashboard.png`。
- Visually verified the desktop composition: aggregate-only metrics, service health, redacted security log, admin navigation, quick links, and client-encryption boundary are present.
- Remaining: create and verify the mobile admin dashboard variant, then document the final screen IDs.
- 已生成移动端管理后台界面 `61624571ebd3464886055f90d676a3c8`，并保存 `../frontend/design-reference/stitch-admin-dashboard-mobile.png`；视觉检查确认 2×2 指标、告警、快捷操作和系统健康列表适配移动端布局。
- Applied a targeted Stitch DOM correction to replace all mobile system-health `Normal` labels with `正常`; the returned project update event confirmed all four replacements.
- 已更新 `DESIGN_REFERENCE.md`，补充桌面端和移动端管理后台界面标识及本地参考资源；未修改前端生产代码。

### 2026-07-21: Flyway 根治

- **Status:** complete
- 根因：MySQL DDL 自动提交 + 进程中断 → 历史表 `success=0`；以及 Java 编译/运行版本不一致导致迁移后崩溃。
- 已落地：`FlywayConfig`（repair → migrate）、V1/V2/V4/V6 幂等 SQL、`java.version=21`、文档与紧急脚本说明。
- 验证：`mvnw.cmd test` 13 通过；本机 MySQL 启动日志显示 repair + `migrationsExecuted=0, success=true`。

## 5-Question Reboot Check

| Question | Answer |
| --- | --- |
| Where am I? | Phase 1：需求与约束提取 |
| Where am I going? | 官方调研、方案决策、文档编写、校验交付 |
| What's the goal? | 完成可指导后续开发的技术选型文档 |
| What have I learned? | 当前需求要求多用户、动态字段、客户端加密和管理员不可见明文 |
| What have I done? | 已初始化规划文件并记录已知需求 |
