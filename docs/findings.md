# Findings & Decisions: 在线账密保险箱技术选型

## Requirements

- 当前实施优先级调整为先搭建后端，前端暂不初始化。
- 用户明确开发环境暂不使用 Docker；后端开发配置改为直连本机 MySQL 8.4，自动化测试使用内存数据库，Docker/Testcontainers 不作为本轮运行前提。

- 多个个人用户分别注册、登录并保存自己的账密。
- 个人用户入口与管理员入口分离，并实施后端角色校验。
- 管理员负责用户、邀请码、系统模板和运行状态，但不能读取用户账密明文。
- 用户通过动态字段保存不同平台、不同渠道提供的非固定资料。
- 支持系统模板、私人模板及模板快照，模板更新不能破坏旧记录。
- 第一版以手动录入为核心，增强能力包括粘贴文本识别。
- 用户需要从电脑和手机浏览器访问并同步数据。
- 账密内容在浏览器端加密，服务端只保存密文。
- 区分网站登录密码、保险箱主密码和恢复密钥。
- 需要搜索、筛选、复制、TOTP、自动锁定、加密备份和设备会话管理。
- 默认邀请码注册，并为未来开放注册保留能力。
- 第一版个人用户注册字段确定为：手机号、用户名、密码、确认密码。
- 第一版注册不进行短信验证码校验；手机号需保存为未验证状态，不能直接作为可信找回凭证。
- 手机号与用户名均需标准化并保证唯一，确认密码只参与校验、不落库。
- Windows 为主要开发环境，生产环境待定。
- 用户指定后端使用 Java 和 Spring Boot。
- 用户指定数据库使用 MySQL 8 系列。
- 用户指定前端使用 Vue 和 Vuetify。

## Research Findings

- 后端搭建环境：当前 Git 分支为 `dev`，跟踪 `origin/dev`；远程基线提交为 `922257c`。
- 本机可用 Java 为 Amazon Corretto 21.0.10 LTS，没有发现 JDK 25；Maven 命令可用，版本为 3.9.9。
- 本机未安装或未配置 Docker，且用户明确开发环境暂不使用 Docker；本轮不创建 Docker 开发依赖，真实 MySQL 验证使用本机 MySQL 8.4 环境。
- 本机存在 MySQL 8.4.8 客户端/安装目录，可进一步确认服务状态，用于无 Docker 时的本地 MySQL 验证。
- 本机默认按 Java 21 构建（`pom.xml` 的 `java.version=21`），避免「按 25 编译、用 21 运行」导致进程在 Flyway 之后崩溃；有 JDK 25 时可 `-Djava.version=25`。
- 后端测试：`mvnw.cmd test`（13 项）通过；真实 MySQL 启动已验证 Flyway repair + migrate。
- Spring Boot 4.1 使用 Jackson 3，应用代码的 `ObjectMapper` 必须导入 `tools.jackson.databind.ObjectMapper`，不能使用 Spring Boot 3 常见的 `com.fasterxml.jackson.databind` 包名。
- MySQL DDL 非事务：半成功迁移会留下 `success=0`。根治方案为 `FlywayConfig` 启动前 `repair()` + 幂等迁移脚本（`IF NOT EXISTS` / 条件 DDL），不再依赖手工修 `flyway_schema_history`。

- 需求要求零知识方向的客户端加密：服务端和管理员不能读取用户记录明文。
- “记录名称、平台、渠道、标签”等内容同样可能泄露用户使用情况，应默认纳入加密载荷；服务端仅保留同步、版本、所有权和状态所需的最少元数据。
- 动态字段必须作为带版本的结构化载荷保存，模板实例需要快照，不能只保存对当前模板的引用。
- 多设备同步意味着不能简单用主密码直接逐条加密：更适合生成随机保险箱数据密钥，再分别用主密码派生密钥和恢复密钥包装该数据密钥。
- 用户认证与保险箱解密是两个独立安全域，需要分别设计。
- 选型层级包括：运行时与仓库、前端、API、契约校验、认证与会话、数据库与迁移、缓存限流、客户端密码学、TOTP、邮件、测试、日志、容器和 HTTPS 入口。
- Node.js 官方在 2026-07 将 v24 标为 LTS、v26 标为 Current，并明确生产应用只应使用 Active LTS 或 Maintenance LTS；因此当前生产基线应选 Node.js 24 LTS，不追随尚未进入 LTS 的 v26。
- Vue 官方推荐用 `create-vue` 创建基于 Vite、TypeScript 的 Vue 项目，并要求在构建或 CI 中单独运行 `vue-tsc` 做类型检查；Pinia 是 Vue 核心团队对新应用推荐的状态管理方案。
- PostgreSQL 18 当前为受支持版本，官方列出的当前小版本为 18.4，支持期到 2030-11；新项目可使用 PostgreSQL 18，并跟随当前小版本安全更新。
- Drizzle ORM 官方支持声明 PostgreSQL RLS 策略与角色，也支持迁移管理；适合把多租户 RLS 作为 API 所有权检查之外的第二道防线。
- Better Auth 官方提供邮箱密码、邮箱验证、密码重置、会话、管理员和 2FA 插件，也明确了邮箱枚举保护条件；它可以减少自研认证表和令牌流程，但管理员强制 2FA、邀请码和禁用用户仍需要业务层约束和集成测试。
- Better Auth 的 2FA 文档提醒：密码型登录会被 2FA 拦截，但密码less 流程默认不一定受同样门控；第一版不启用 OAuth、魔法链接或无密码登录，避免绕过管理员 2FA 策略。
- NestJS 官方支持 Express 和 Fastify 适配器，但平台专属中间件不能直接混用。Better Auth 的 NestJS 集成由社区维护，且其 Fastify 支持仍标为 beta；在安全核心路径上直接采用“NestJS + Fastify + 社区桥接”会叠加集成风险。
- Better Auth 原生支持 PostgreSQL 和 Drizzle 适配器，其核心处理器基于标准 Request/Response；与有官方集成的框架组合比依赖 beta 桥接更稳妥。
- Libsodium 的高层 `crypto_pwhash` 当前使用 Argon2id；RFC 9106 也推荐 Argon2id。浏览器端参数必须按目标手机和电脑做基准测试，不能照搬高内存服务端参数。
- Libsodium 官方文档推荐在不要求跨库互操作时使用 XChaCha20-Poly1305；其 192 位 nonce 允许安全使用随机 nonce，但同一密钥下仍禁止实际复用 nonce。
- W3C Web Crypto API 提供 AES-GCM、HKDF、PBKDF2、HMAC 和安全随机数等原语，但当前标准能力不包含 Argon2id 或 XChaCha20-Poly1305；如选用推荐密码学组合，需要使用官方 libsodium.js WebAssembly/JavaScript 包并先做浏览器兼容、包体和内存压测。
- TOTP 应遵循 RFC 6238，并只在浏览器本地从解密后的种子计算；无需把 TOTP 种子或动态验证码发送给服务端。
- Hono 提供 Node.js 适配器、Zod 请求校验、CSRF、安全响应头和类型化 RPC；Better Auth 也提供直接的 Hono 集成，不需要社区桥接层。
- Hono RPC 可在 monorepo 中从服务端路由推导客户端请求和响应类型，但大型路由树可能拖慢 TypeScript；需要按领域拆分客户端，并在构建时生成/固化类型。
- Nuxt 4.5 当前可部署为 Node.js 服务，并有 Better Auth 官方集成；但本产品无需 SEO，保险箱又应完全在客户端解密。采用 SSR 会增加服务端渲染、cookie 转发和水合边界，收益有限。
- 最稳妥的浏览器边界是同源部署：Caddy 同时提供 Vue 静态文件并将 `/api/*` 反向代理到 API。这样生产环境不需要跨域 Cookie，能保留 `SameSite=Lax` 并简化 CSRF 控制。
- Better Auth 默认使用 Node.js 原生 scrypt 保存网站登录密码，官方允许自定义 Argon2。第一版保持其默认 scrypt，减少自定义认证代码；保险箱主密码仍独立使用客户端 Argon2id。
- Better Auth 管理员插件支持角色、禁用用户和撤销会话，但默认管理员权限包含 impersonate、set-password 等本产品不应开放的能力；必须定义最小化自定义权限集，禁止管理员模拟个人用户或替用户设置密码。
- Better Auth 自带限流，但默认内存存储不适合多实例；第一版单实例也应使用数据库限流存储，达到扩容条件后再接 Redis，避免一开始引入额外基础设施。
- Playwright 能在 Windows/Linux/macOS 上测试 Chromium、Firefox 和 WebKit，并支持移动设备模拟；应把真实浏览器中的 libsodium/WASM、自动锁定、跨设备同步和权限隔离列为关键 E2E 测试。
- Caddy 可自动管理 HTTPS、HTTP 重定向和反向代理，适合单机 Docker 部署；如果前方再加 CDN，必须明确可信代理范围，不能直接信任外部传入的转发头。
- PostgreSQL RLS 默认在没有适用策略时拒绝访问，但超级用户、BYPASSRLS 角色和通常的表所有者可能绕过；应用运行账号必须是非所有者、无 BYPASSRLS，并对账密表启用 `FORCE ROW LEVEL SECURITY`。
- RLS 不能替代 API 层授权，外键和唯一约束也可能形成信息侧信道；公开错误信息必须归一化，避免借约束错误确认其他用户数据存在。
- 密文、nonce 和包装后的密钥应使用 PostgreSQL `bytea`，而不是可查询的 `jsonb`。客户端动态字段 JSON 在浏览器序列化后整体加密，数据库无需理解内部字段。
- PostgreSQL 备份需要定期执行并实际恢复演练；`pg_dump -Fc` 适合第一版的可迁移备份，但数据库角色/全局对象需单独纳入备份或基础设施脚本。
- UI 建议使用 Tailwind CSS 构建本地静态 CSS，不使用运行时 CDN；Reka UI 提供 Vue 的无样式、可访问性原语和键盘/焦点管理，适合作为对话框、菜单、选择框等交互基础。
- Docker Compose 支持逐服务授予文件型 secrets，并可等待 PostgreSQL healthcheck 后启动 API；生产敏感配置不应直接烘焙进镜像或提交到 `.env`。
- Zod 4 当前为稳定版，支持 TypeScript 严格模式和浏览器/Node 运行；适合共享动态字段、加密信封和 API 输入契约，但服务端仍只校验密文信封结构，不能校验被加密的内部字段值。
- Vitest Browser Mode 可使用 Playwright 在真实 Chromium/Firefox/WebKit 环境运行；密码学包应同时有 Node 单元测试、真实浏览器向量测试和 Playwright 全流程测试。
- Nodemailer 的 SMTP 传输能保持邮件服务商无关，并支持强制 TLS；生产环境必须关闭原始 SMTP debug，避免邮件正文或令牌进入日志。
- Pino 支持按路径清除 Cookie、Authorization、密码等字段。日志脱敏只作为第二道保护，首要原则仍是从不把请求体、密文解密内容或邮件令牌传给日志调用。
- libsodium.js 使用 WebAssembly 时，严格 CSP 需要允许更窄的 `script-src 'wasm-unsafe-eval'`；不能退化为 `'unsafe-eval'`。这项 CSP 与 libsodium 加载方式必须在技术原型中实际验证。
- Spring Boot 当前稳定文档为 4.1.0，要求 Java 17+，兼容到 Java 26，并配套 Spring Framework 7.0.8+。Java 25 是当前 LTS；新项目推荐 Java 25 LTS + Spring Boot 4.1.x，并由 Spring Boot BOM 管理 Spring Security 版本。
- MySQL 8.4 是官方 LTS 分支，官方说明 LTS 系列以稳定行为和长期支持为目标；用户所说的“MySQL 8”应具体落为 MySQL 8.4.x LTS，而不是新建在旧 8.0 分支上。当前已发布小版本为 8.4.10，部署时跟随 8.4 最新补丁版。
- Spring Security 7.1 当前提供统一的认证、请求授权、方法授权、会话管理和常见漏洞防护；本项目必须使用，不能以自研拦截器替代。
- 对同源 Vue SPA，推荐服务端 Session Cookie，而不是把长期 JWT 放在浏览器存储。Spring Security 原生管理 SecurityContext 和并发会话，Spring Session JDBC 可把 HttpSession 存入 MySQL，支持多实例和后台撤销会话。
- Spring Security 默认对不安全 HTTP 方法启用 CSRF，7.x 提供 SPA 专用 `.csrf(csrf -> csrf.spa())` 配置；登录和退出也必须受 CSRF 保护，前端启动、登录成功和退出成功后都要刷新 CSRF token。
- Java 25 LTS 与 Spring Boot 4.1.x 虽然当前兼容，但属于较新的生产基线；在项目初始化阶段要用真实依赖做一次完整构建和 Testcontainers MySQL 集成测试，防止周边库尚未适配。
- Vue 前端采用 Vue 3 + TypeScript + Vite，CI 中独立执行 `vue-tsc`；Pinia 3 用于会话视图状态和解锁后的内存态，但严禁通过持久化插件把保险箱明文写入 localStorage。
- Vuetify 官方当前安装页已进入 Vuetify 4，官方仓库当前最新稳定标签为 4.0.7（2026-05-08）。新项目可优先采用 Vuetify 4，但因主版本较新，需要先验证数据表格、动态表单、主题和移动端关键组件；若原型发现阻塞，再评估 Vuetify 3 维护线。
- 后端数据访问采用 Spring Data JPA 可减少基础 CRUD 样板，配合显式 ownerId 查询、事务和审计；不能使用无 owner 条件的通用 `findById` 访问用户密文对象。
- 数据库结构使用 Flyway SQL 迁移，Hibernate 只做 `ddl-auto=validate`，禁止生产自动建表或改表。Flyway 的 MySQL 支持需要单独引入 `flyway-mysql` 模块，并通过 MySQL 8.4 Testcontainers 验证兼容。
- Spring Security 7 的多因素认证模型可以通过因子权限保护管理接口，但官方 One-Time Token 属于一次性登录链接/令牌，不等同于认证器应用的 TOTP；管理员 TOTP 需要单独选择 RFC 6238 实现并做原型验证。
- Spring Boot Actuator 可以使用独立管理端口或地址，第一版只开放必要健康检查并限制在内部网络。
- MySQL 逻辑备份可以使用 `mysqldump --single-transaction`；数据量增加后可切换 MySQL Shell 并行转储。无论选择哪种工具，都必须进行恢复演练。
- Testcontainers MySQL 用于验证 MySQL 8.4、Flyway、JPA 和唯一约束的真实兼容性，不能只依赖 H2 替代数据库测试。

## Technical Decisions

| Decision | Rationale |
| --- | --- |
| Java + Spring Boot | 用户指定的后端方向 |
| MySQL 8 系列 | 用户指定的数据库方向，具体分支待核验 |
| Vue 3 + Vuetify | 用户指定的前端方向 |
| 采用 Spring Security | 账密保险箱需要成熟、统一的认证、会话、CSRF 和角色授权能力，轻量自研方案风险不合理 |
| 不采用 Node.js + Hono 推荐方案 | 与用户明确指定的 Java 技术方向冲突 |
| 采用 Java 25 LTS + Spring Boot 4.1.x | 使用当前 LTS Java，并由 Boot BOM 管理框架依赖 |
| 采用 MySQL 8.4.x LTS | 将用户指定的 MySQL 8 明确落到官方 LTS 分支 |
| 采用 Spring Session JDBC 和同源 Cookie | 支持会话撤销，避免长期 JWT 写入浏览器存储 |
| 采用 Vue 3 + TypeScript + Vite + Vuetify 4 | 满足用户前端约束并提供类型与组件体系 |
| 采用 libsodium.js 客户端信封加密 | 后端只保存密文，满足管理员不可读边界 |
| 第一版采用模块化单体和 Docker Compose | 降低部署复杂度，同时保留清晰模块边界 |

## Issues Encountered

| Issue | Resolution |
| --- | --- |
| 更新多个规划文件时上下文匹配失败 | 拆分为逐文件补丁，并按实际原文更新 |

## Resources

- 产品需求：`D:\Ai\online-word\在线账密保险箱-产品需求文档-v1.0.md`
- Node.js Releases: https://nodejs.org/en/about/previous-releases
- Vue TypeScript Guide: https://vuejs.org/guide/typescript/overview
- Vue State Management: https://vuejs.org/guide/scaling-up/state-management
- PostgreSQL Versioning Policy: https://www.postgresql.org/support/versioning/
- PostgreSQL 18 Documentation: https://www.postgresql.org/docs/18/
- Drizzle PostgreSQL RLS: https://orm.drizzle.team/docs/rls
- Better Auth Email and Password: https://better-auth.com/docs/authentication/email-password
- Better Auth Email: https://better-auth.com/docs/concepts/email
- Better Auth 2FA: https://better-auth.com/docs/plugins/2fa
- Better Auth NestJS Integration: https://better-auth.com/docs/integrations/nestjs
- NestJS Fastify: https://docs.nestjs.com/techniques/performance
- Libsodium Password Hashing: https://doc.libsodium.org/password_hashing
- Libsodium XChaCha20-Poly1305: https://doc.libsodium.org/secret-key_cryptography/aead/chacha20-poly1305/xchacha20-poly1305_construction
- libsodium.js: https://github.com/jedisct1/libsodium.js
- RFC 9106 Argon2: https://www.rfc-editor.org/rfc/rfc9106
- RFC 6238 TOTP: https://www.rfc-editor.org/rfc/rfc6238
- W3C Web Crypto API: https://www.w3.org/TR/webcrypto/
- Hono Node.js: https://hono.dev/docs/getting-started/nodejs
- Hono Validation: https://hono.dev/docs/guides/validation
- Hono RPC: https://hono.dev/docs/guides/rpc
- Hono CSRF: https://hono.dev/docs/middleware/builtin/csrf
- Hono Secure Headers: https://hono.dev/docs/middleware/builtin/secure-headers
- Better Auth Hono Integration: https://better-auth.com/docs/integrations/hono
- Better Auth Security: https://better-auth.com/docs/reference/security
- Better Auth Admin: https://better-auth.com/docs/plugins/admin
- Better Auth Rate Limit: https://better-auth.com/docs/concepts/rate-limit
- Nuxt Deployment: https://nuxt.com/docs/4.x/getting-started/deployment
- Better Auth Nuxt Integration: https://better-auth.com/docs/integrations/nuxt
- Playwright: https://playwright.dev/docs/intro
- Caddy HTTPS: https://caddyserver.com/docs/quick-starts/https
- Caddy Reverse Proxy: https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
- PostgreSQL Row Security: https://www.postgresql.org/docs/18/ddl-rowsecurity.html
- PostgreSQL Data Types: https://www.postgresql.org/docs/18/datatype.html
- PostgreSQL Backup and Restore: https://www.postgresql.org/docs/18/backup.html
- Tailwind CSS: https://tailwindcss.com/docs/installation/using-postcss
- Reka UI Accessibility: https://www.reka-ui.com/docs/overview/accessibility
- Docker Compose Secrets: https://docs.docker.com/compose/how-tos/use-secrets/
- Docker Compose Startup Order: https://docs.docker.com/compose/how-tos/startup-order/
- Zod: https://zod.dev/
- Vitest Browser Mode: https://vitest.dev/guide/browser/
- Nodemailer SMTP: https://nodemailer.com/smtp
- Pino: https://github.com/pinojs/pino
- W3C CSP Level 3: https://www.w3.org/TR/CSP3/
- Spring Boot System Requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Security Reference: https://docs.spring.io/spring-security/reference/
- Spring Security Session Management: https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- Spring Security SPA CSRF: https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html
- Spring Session JDBC: https://docs.spring.io/spring-session/reference/configuration/jdbc.html
- Java SE Support Roadmap: https://www.oracle.com/java/technologies/java-se-support-roadmap.html
- MySQL LTS Releases: https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html
- MySQL 8.4 Release Notes: https://dev.mysql.com/doc/relnotes/mysql/8.4/en/
- Vuetify Installation: https://vuetifyjs.com/en/getting-started/installation/
- Vuetify Repository: https://github.com/vuetifyjs/vuetify
- Spring Data JPA: https://docs.spring.io/spring-data/jpa/reference/
- Flyway MySQL: https://documentation.red-gate.com/fd/mysql-277579322.html

## Visual/Browser Findings

- 暂无。

### 2026-07-20: Existing Stitch authentication references

- User-owned Stitch project: `Online Security Vault` (`10594920934536828879`).
- Login reference: `安全登录 (v3) - 统一风格版` (`7762b4ad9ac04f01bff0ddd6b412fb36`).
- Registration reference: `创建账户 (v2)` (`1aaa6f930bc54addb8e930eb4dd32e42`).
- Visual system: 50/50 desktop split, Sentinel Navy introduction panel, white authentication card with an Action Blue left accent, #F8FAFC canvas, restrained 8px radius, light elevation, and compliance footer.
- 本地参考图：`../frontend/design-reference/stitch-login-v3.png` 和 `../frontend/design-reference/stitch-register-v2.png`。

### 2026-07-20: Homepage optimization task started

- Repository is a very small two-commit project with no historical bug magnets or firefighting commits; implementation risk is concentrated in the current frontend rather than Git history.
- Existing planning notes identify the Stitch project `Online Security Vault` (`10594920934536828879`) as the user-owned design source.
- The homepage optimization scope is to use the requirements document and `Design.md` as constraints, then modify the already-drawn homepage in Stitch before changing local frontend code.

### 2026-07-20: Homepage design constraints extracted

- Product homepage route is `/vault`; its primary responsibilities are record list, local search, filters, sorting, and quick actions.
- Homepage list requirements: filter by platform, channel, status, and tags; search by record name and decrypted local fields; sort by updated/created/expiry time; support card and list views; show record name, platform, channel, status, and updated time; never show passwords, 2FA keys, or other sensitive values in the list.
- Detail actions to preserve in the visual concept: reveal/hide sensitive fields, copy, open URL, generate TOTP locally, edit, archive, delete, and show timestamps.
- Product tone is reliable, restrained, and clear. The interface should avoid security-theater dark styling, dense explanations, and controls that imply unfinished capabilities.
- Design.md homepage-relevant tokens: `#F7F9FC` page background, `#FFFFFF` surfaces, `#155EEF` primary, `#101828` heading text, `#475467` secondary text, `#D0D5DD` borders, 44–48px controls, 10px inputs, 16px cards, and light shadow `0 12px 32px rgba(16,24,40,.08)`.
- Existing local `VaultHomeView.vue` is only a post-login placeholder; it currently has no records, search, filters, view switch, or quick actions. Stitch should therefore be used to define the full information architecture before local implementation.
- Stitch confirmed the existing homepage screen: `保险箱首页 (Vault Dashboard)` (`4f0ecf185e974a07b7fb216362900c54`), desktop, 2560×2440.
- The current Stitch project also contains the requirements document as an uploaded reference and the existing login/register screens, so the homepage can be optimized in the same project and visual language.
- No separate mobile homepage screen appeared in the project screen list; mobile behavior should be included in the optimization prompt rather than assumed to exist already.
- Stitch completed the homepage edit as a new optimized screen rather than mutating the source screen: `3bc78f70199b4793b6184bee023880f8`, desktop, 2560×2048. The original `4f0ecf185e974a07b7fb216362900c54` remains available for comparison.
- The generated version claims the intended changes: Chinese status/navigation copy, separated page search/filter/sort controls, stronger “新增记录” hierarchy, metadata-only record cards, hidden sensitive fields, and mobile sidebar collapse guidance.
- Stitch generated the mobile homepage variant `7717a41a92a34c4a91c1cf475bed0fd1` titled `保险箱首页 (移动端)`, MOBILE, 780×1768 canvas based on a 390px target. It uses a single column, compact app bar/drawer navigation, full-width add action, collapsed filter button, sort control, and metadata-only cards.
- Mobile visual QA found English bottom-navigation labels (`Vault`, `Report`, `Gen`, `Settings`) remaining in the generated image; this violates the Chinese-only UI rule and requires one targeted correction.
- Stitch returned a completed DOM update event for mobile screen `7717a41a92a34c4a91c1cf475bed0fd1`, replacing the four labels with `保险箱 / 安全报告 / 生成器 / 设置` and changing the active color class. The exported screenshot/HTML URL still served a cached pre-edit copy during immediate verification, so the Stitch canvas event is the authoritative confirmation of the patch; the stale export should not be treated as the final visual state.

### 2026-07-21: Admin console design task started

- The admin area must be a separate authenticated route and must not expose any user's account/password/2FA plaintext.
- Admin requirements include user management, invitation-code management, system-template management, security logs, registration policy, announcements, user count, storage usage, service health, encrypted backups, and maintenance operations.
- Admin can enable/disable users and revoke sessions, but cannot unlock a user's vault, obtain the vault master password/recovery key, or migrate a user's plaintext credentials.
- The admin homepage should therefore emphasize operational status, actionable alerts, and aggregate metrics; it should not display credential records or sensitive user content.
- Admin navigation should map to `/admin`, `/admin/users`, `/admin/invitations`, `/admin/templates`, `/admin/security-logs`, and `/admin/settings`.
- Admin dashboard information architecture should include user count/status, encrypted storage usage, service health, security alerts, recent safe admin activity, and clear links to the six admin modules. Any user examples must be aggregate or redacted.
- Apply Design.md tokens: `#F7F9FC` canvas, white surfaces, `#155EEF` primary action, `#101828` headings, `#475467` secondary text, `#D0D5DD` borders, 16px large cards, 10px controls, light elevation, Chinese UI copy, and icon+text+color status feedback.
- No admin screen existed in the project before this task. Stitch generated `管理后台首页 (系统概览)` as screen `e81db235f42a4b13b88152960ae0f62a`, DESKTOP, 2560×2510, using the existing `assets/81edc99eeb404081a72fad143af36f6c` design system.
- Generated structure includes admin navigation, four aggregate metric cards, security reminders, service health, redacted security events, quick links, and the explicit client-encryption/admin-visibility boundary.
- Stitch generated the mobile admin variant `管理后台首页 (移动端)` as screen `61624571ebd3464886055f90d676a3c8`, MOBILE, 780×2566 canvas based on a 390px target. It uses a compact top bar, 2×2 metric grid, stacked security alerts, quick-action grid, and system-health list without sensitive vault data.
- Mobile admin visual QA found the system-health status chips still use the English word `Normal`; this conflicts with the project Chinese-first rule and needs a targeted label correction.
- Stitch returned a completed DOM update event for mobile admin screen `61624571ebd3464886055f90d676a3c8`, replacing all four system-health `Normal` labels with `正常` while preserving layout and styling.
