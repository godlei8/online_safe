# Online Safe Backend

在线账密保险箱的 Spring Boot 后端。当前已经包含工程基础设施、个人用户注册、用户名/手机号登录、Session、CSRF、管理员登录、邀请码管理和用户/管理员接口隔离。

## 环境要求

本地开发配置文件 `src/main/resources/application-local.yml` 不纳入版本控制。首次配置时，可复制同目录的 `application-local.example.yml`，再填入自己的本机数据库密码。

- Java：默认按 **JDK 21** 编译与运行（`pom.xml` 中 `java.version=21`）。具备 JDK 25 时可加 `-Djava.version=25`。
- Maven：项目使用 Maven Wrapper。
- 本地数据库：MySQL 8.4，不依赖 Docker。

## 创建本地数据库

使用具有建库权限的 MySQL 管理账号执行以下 SQL，并把示例密码换成本机开发密码：

```sql
CREATE DATABASE online_safe
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'online_safe'@'localhost' IDENTIFIED BY '请替换为本机开发密码';
GRANT ALL PRIVILEGES ON online_safe.* TO 'online_safe'@'localhost';
FLUSH PRIVILEGES;
```

不要把真实数据库密码写入配置文件或提交到 Git。

## PowerShell 启动

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/online_safe?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME = 'online_safe'
$env:DB_PASSWORD = '你的本机开发密码'
$env:SESSION_COOKIE_SECURE = 'false'

Set-Location D:\Ai\online-word\backend
.\mvnw.cmd spring-boot:run
```

启动时 Flyway 会自动创建个人用户、管理员、邀请码和 Spring Session 表，并在不存在 `admin` 时初始化管理员账户（密码仅以 BCrypt 哈希保存）。

本地默认管理员（仅开发）：

- 用户名：`admin`
- 初始口令由本机开发迁移设置；不在文档、日志或版本库中保存或展示。

首次登录后应尽快修改密码。注册邀请码需由管理员在后台创建。

## Flyway（已从根上适配 MySQL）

MySQL 的 DDL **不能事务回滚**。若进程在「DDL 已提交、历史表尚未标成功」之间退出，会出现 `Detected failed migration`。

项目已做三件事：

1. **启动策略**：`FlywayConfig` 在 migrate 前执行 `repair()`（可用 `app.flyway.repair-before-migrate` / `FLYWAY_REPAIR_BEFORE_MIGRATE` 关闭）。
2. **幂等脚本**：`V1`/`V2`/`V4`/`V6` 等使用 `IF NOT EXISTS` 或条件 DDL，重跑不会因对象已存在而失败。
3. **编译版本对齐**：默认 Java 21，避免“按 25 编译、用 21 运行”导致 Flyway 之后进程崩溃。

编写新迁移时必须可重复执行，例如：

- 建表：`CREATE TABLE IF NOT EXISTS ...`，索引尽量写在建表语句内。
- 加列：先查 `information_schema.COLUMNS`，不存在再 `ALTER TABLE ... ADD COLUMN`。
- 种子数据：`INSERT ... SELECT ... WHERE NOT EXISTS`。

极端情况下可手动执行：

```powershell
Set-Location D:\Ai\online-word\backend
$env:DB_PASSWORD = '你的本机开发密码'
.\scripts\repair-flyway.ps1
.\mvnw.cmd spring-boot:run
```

## 测试

测试使用内存数据库，不要求本机 MySQL 或 Docker运行：

```powershell
Set-Location D:\Ai\online-word\backend
.\mvnw.cmd test
```

## 当前接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/csrf` | 获取 SPA 写请求需要的 CSRF Token |
| POST | `/api/auth/register` | 手机号、用户名、密码、确认密码、数据库邀请码注册 |
| POST | `/api/auth/login` | 使用用户名或手机号登录 |
| POST | `/api/auth/logout` | 退出当前个人 Session |
| GET | `/api/auth/session` | 查询当前个人用户登录状态 |
| POST | `/api/admin/auth/login` | 管理员用户名密码登录 |
| POST | `/api/admin/auth/logout` | 退出当前管理员 Session |
| GET | `/api/admin/auth/session` | 查询当前管理员登录状态 |
| GET | `/api/admin/v1/invitations` | 邀请码分页列表 |
| GET | `/api/admin/v1/invitations/stats` | 邀请码统计 |
| POST | `/api/admin/v1/invitations` | 创建邀请码（明文仅返回一次） |
| GET | `/api/admin/v1/invitations/{id}/plain-code` | 获取可复制的明文（列表不展示） |
| DELETE | `/api/admin/v1/invitations/{id}` | 删除邀请码 |
| GET | `/api/admin/v1/users` | 用户分页列表（手机号脱敏） |
| GET | `/api/admin/v1/users/stats` | 用户统计 |
| POST | `/api/admin/v1/users/{id}/disable` | 禁用用户并失效会话 |
| POST | `/api/admin/v1/users/{id}/enable` | 启用用户 |
| POST | `/api/admin/v1/users/{id}/revoke-sessions` | 使用户会话失效 |
| GET | `/api/v1/ping` | 验证个人用户权限 |
| GET | `/api/admin/v1/ping` | 验证管理员权限 |
| GET | `/actuator/health` | 健康检查 |

所有写请求都需要先获取 `/api/csrf`，并通过响应指定的请求头回传 Token。

## 当前边界

本批次尚未实现：

- 管理员 TOTP。
- 注册限流。
- 保险箱主密码、恢复密钥和密文记录接口。
- 动态字段、模板、审计和设备会话管理。
- 保险箱密文存储量/记录数真值、系统模板、安全日志业务接口。
