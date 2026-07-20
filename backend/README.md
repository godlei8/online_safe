# Online Safe Backend

在线账密保险箱的 Spring Boot 后端。当前已经包含工程基础设施、个人用户注册、用户名/手机号登录、Session、CSRF 和用户/管理员接口隔离骨架。

## 环境要求

本地开发配置文件 `src/main/resources/application-local.yml` 不纳入版本控制。首次配置时，可复制同目录的 `application-local.example.yml`，再填入自己的本机数据库密码。

- 正式目标：Java 25 LTS。
- Maven：项目使用 Maven Wrapper；生成 Wrapper 前也可以使用本机 Maven 3.9+。
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
$env:REGISTRATION_INVITATION_CODE = '请设置本地开发邀请码'

Set-Location D:\Ai\online-word\backend
.\mvnw.cmd spring-boot:run
```

启动时 Flyway 会自动创建个人用户、管理员和 Spring Session 表，并在不存在 `admin` 时初始化管理员账户（密码仅以 BCrypt 哈希保存）。

## 测试

测试使用内存数据库，不要求本机 MySQL 或 Docker运行：

```powershell
Set-Location D:\Ai\online-word\backend
.\mvnw.cmd test
```

当前机器只有 Java 21 时，可以临时执行兼容性构建：

```powershell
.\mvnw.cmd -Djava.version=21 test
```

正式发布前仍需在 Java 25 环境重新执行完整测试。

## 当前接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/csrf` | 获取 SPA 写请求需要的 CSRF Token |
| POST | `/api/auth/register` | 手机号、用户名、密码、确认密码注册 |
| POST | `/api/auth/login` | 使用用户名或手机号登录 |
| POST | `/api/auth/logout` | 退出当前 Session |
| GET | `/api/auth/session` | 查询当前个人用户登录状态 |
| GET | `/api/v1/ping` | 验证个人用户权限 |
| GET | `/api/admin/v1/ping` | 验证管理员权限，目前没有开放管理员登录 |
| GET | `/actuator/health` | 健康检查 |

所有 POST 请求都需要先获取 `/api/csrf`，并通过响应指定的请求头回传 Token。

## 当前边界

本批次尚未实现：

- 管理员注册、登录和 TOTP。
- 注册限流。
- 保险箱主密码、恢复密钥和密文记录接口。
- 动态字段、模板、审计和设备会话管理。
- 前端页面。
