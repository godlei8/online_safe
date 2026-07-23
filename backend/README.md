# Online Safe 后端

在线账密保险箱的 Spring Boot 后端。涵盖个人认证（短信注册 / 登录 / 重置密码）、服务端加密的保险箱与私人模板、系统公告，以及管理员侧的邀请码、用户与公告管理。

## 环境要求

本地开发配置 `src/main/resources/application-local.yml` **不纳入版本控制**。首次可复制同目录 `application-local.example.yml`，再填入本机数据库等信息。

- Java：默认 **JDK 21**（`pom.xml` 中 `java.version=21`）。具备 JDK 25 时可加 `-Djava.version=25`。
- Maven：使用项目自带 Maven Wrapper。
- 本地数据库：MySQL 8.4（可不依赖 Docker）。测试使用 H2 内存库。

## 创建本地数据库

使用具有建库权限的 MySQL 账号执行（请替换密码）：

```sql
CREATE DATABASE online_safe
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'online_safe'@'localhost' IDENTIFIED BY '请替换为本机开发密码';
GRANT ALL PRIVILEGES ON online_safe.* TO 'online_safe'@'localhost';
FLUSH PRIVILEGES;
```

不要把真实数据库密码写入版本库。

## PowerShell 启动

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/online_safe?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME = 'online_safe'
$env:DB_PASSWORD = '你的本机开发密码'
$env:SESSION_COOKIE_SECURE = 'false'

Set-Location D:\Ai\online-word\backend
.\mvnw.cmd spring-boot:run
```

启动时 Flyway 自动迁移（当前至 **V12**）。不存在 `admin` 时会初始化管理员（口令仅以 BCrypt 哈希保存）。

本地默认管理员（仅开发）：

- 用户名：`admin`
- 初始口令由本机开发迁移设置；不在文档、日志或版本库中展示。

首次登录后应尽快修改密码。

本地短信默认 `SMS_PROVIDER=logging`（或未配置阿里云密钥时降级为打日志），验证码出现在应用日志中，便于联调注册与重置密码。

## 重要配置键（勿提交真实值）

| 类别 | 环境变量 / 配置 |
|---|---|
| 数据库 | `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` |
| Session | `SESSION_COOKIE_SECURE` |
| Flyway | `FLYWAY_REPAIR_BEFORE_MIGRATE`（`app.flyway.repair-before-migrate`） |
| 保险箱加密 | `VAULT_ENCRYPTION_KEY`、`VAULT_CURRENT_KEY_ID` |
| 邀请码加密 | `INVITATION_ENCRYPTION_KEY` |
| 短信 | `SMS_PROVIDER`、`ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET`、`ALIYUN_SMS_SIGN_NAME`、`ALIYUN_SMS_TEMPLATE_REGISTER`、`ALIYUN_SMS_TEMPLATE_RESET`、`SMS_CODE_TTL_SECONDS`、`SMS_CODE_LENGTH`、`SMS_SEND_INTERVAL_SECONDS`、`SMS_SEND_DAILY_LIMIT` |

生产部署见仓库根目录 `deploy/` 与 `docs/Docker生产部署*.md`。生产短信走阿里云号码认证（PNVS）类接口，需保证 backend 容器可出网。

## Flyway（已适配 MySQL）

MySQL 的 DDL **不能事务回滚**。若进程在「DDL 已提交、历史表尚未标成功」之间退出，会出现 `Detected failed migration`。

项目已做：

1. **启动策略**：`FlywayConfig` 在 migrate 前执行 `repair()`（可用配置关闭）。
2. **幂等脚本**：关键迁移使用 `IF NOT EXISTS` 或条件 DDL。
3. **编译版本对齐**：默认 Java 21，避免“按高版本编译、用低版本运行”导致崩溃。

编写新迁移时必须可重复执行（建表 `IF NOT EXISTS`、加列先查 `information_schema`、种子 `INSERT ... WHERE NOT EXISTS`）。

极端情况可手动：

```powershell
Set-Location D:\Ai\online-word\backend
$env:DB_PASSWORD = '你的本机开发密码'
.\scripts\repair-flyway.ps1
.\mvnw.cmd spring-boot:run
```

### 迁移一览（摘要）

| 版本 | 内容 |
|---|---|
| V1–V7 | 身份、Session、管理员种子、邀请码、用户 last_login |
| V8 | 保险箱表 |
| V9 | 密保表（保留表结构；注册/重置流程已不再使用） |
| V10 | 服务端加密改造（清空旧客户端密文，删除 key-bundle） |
| V11 | 系统公告 |
| V12 | 短信验证码 |

## 测试

测试使用内存库，不要求本机 MySQL：

```powershell
Set-Location D:\Ai\online-word\backend
.\mvnw.cmd test
```

## 当前接口

写请求需先 `GET /api/csrf`，并按响应要求回传 CSRF Token。Session Cookie：`ONLINE_SAFE_SESSION`（空闲约 1 小时）。  
`/api/v1/**` → `ROLE_USER`；`/api/admin/**` → `ROLE_ADMIN`。

### 认证与安全

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/csrf` | SPA 写请求 CSRF Token |
| POST | `/api/auth/register` | 手机号 + 短信验证码 + 用户名 + 密码注册（**不强制邀请码**） |
| POST | `/api/auth/login` | 用户名或手机号登录 |
| POST | `/api/auth/logout` | 退出个人 Session |
| GET | `/api/auth/session` | 个人登录状态 |
| POST | `/api/auth/sms/send` | 发送短信（`REGISTER` / `RESET_PASSWORD`） |
| POST | `/api/auth/password-reset/confirm` | 短信验证后重置密码（吊销会话，保留保险箱） |
| POST | `/api/admin/auth/login` | 管理员登录 |
| POST | `/api/admin/auth/logout` | 管理员退出 |
| GET | `/api/admin/auth/session` | 管理员登录状态 |

### 保险箱（服务端加解密）

API 对已登录用户收发明文 `payload`；库内为 AES-GCM 密文。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST | `/api/v1/vault/items` | 记录列表 / 新建 |
| GET / PUT / DELETE | `/api/v1/vault/items/{id}` | 记录详情 / 更新 / 删除 |
| GET / POST | `/api/v1/vault/private-templates` | 私人模板列表 / 新建 |
| GET / PUT / DELETE | `/api/v1/vault/private-templates/{id}` | 私人模板详情 / 更新 / 删除 |

### 用户公告

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/announcements/inbox` | 收件箱（含未读） |
| GET | `/api/v1/announcements` | 公告列表 |
| POST | `/api/v1/announcements/{id}/read` | 标记已读 |

### 管理端

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST | `/api/admin/v1/invitations` | 邀请码列表 / 创建（明文仅返回一次） |
| GET | `/api/admin/v1/invitations/stats` | 邀请码统计 |
| GET | `/api/admin/v1/invitations/{id}/plain-code` | 获取可复制明文 |
| DELETE | `/api/admin/v1/invitations/{id}` | 删除邀请码 |
| GET | `/api/admin/v1/users` | 用户分页（手机号脱敏） |
| GET | `/api/admin/v1/users/stats` | 用户统计 |
| POST | `/api/admin/v1/users/{id}/disable` | 禁用并失效会话 |
| POST | `/api/admin/v1/users/{id}/enable` | 启用 |
| POST | `/api/admin/v1/users/{id}/revoke-sessions` | 吊销会话 |
| GET / POST | `/api/admin/v1/announcements` | 公告列表 / 创建 |
| PUT | `/api/admin/v1/announcements/{id}` | 更新公告 |
| POST | `/api/admin/v1/announcements/{id}/publish` | 发布 |
| POST | `/api/admin/v1/announcements/{id}/offline` | 下线 |

### 探针

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/ping` | 个人权限探针 |
| GET | `/api/admin/v1/ping` | 管理员权限探针 |
| GET | `/actuator/health` | 健康检查 |

## 当前边界

- 管理员 TOTP、系统模板、安全日志业务接口未实现。
- 注册全局限流尚未完善（短信侧有发送间隔与日限额等配置）。
- 邀请码后台仍可用，但**注册不再校验邀请码**。
- 密保相关 HTTP 已下线；`user_security_question` 表保留不参与现行流程。
- 重置密码仅吊销 Session，**不清空**保险箱。
- V10 会清空旧客户端密文；从零知识方案迁到服务端加密后需用户重新录入历史数据（若曾使用旧模型）。
- 管理员 API 看不到用户明文，但持有 `VAULT_ENCRYPTION_KEY` 的运维环境具备解密能力，**非零知识**。
