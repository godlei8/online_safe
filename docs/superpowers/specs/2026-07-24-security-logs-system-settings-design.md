# 安全日志与系统设置设计规格

> 状态：方案稿，待确认后进入实施计划  
> 日期：2026-07-24  
> 适用版本：管理后台 MVP  
> 目标路由：`/admin/security-logs`、`/admin/settings`

## 1. 背景

当前项目已经具备普通用户与管理员独立认证、用户管理、会话失效、邀请码、公告、系统模板和个人中心，但管理后台的“安全日志”和“系统设置”仍是占位页面。

这两个模块需要解决两个问题：

1. 对登录、密码重置和管理员高权限操作形成可追溯记录。
2. 允许管理员调整少量业务策略，同时禁止在线修改数据库、加密密钥和云服务凭据等部署级配置。

本方案继续遵循项目的核心边界：

- 管理员管理系统，不管理用户的秘密。
- 日志和设置页面不得出现保险箱账号、密码、备注、动态字段值等明文。
- 系统设置不是通用配置中心，只开放经过代码审查的白名单策略。

## 2. 目标

### 2.1 安全日志

- 记录用户和管理员认证事件。
- 记录管理员对用户、会话、邀请码、公告、模板和系统设置的关键操作。
- 支持按时间、事件、结果、风险等级和主体查询。
- 提供经过脱敏的来源网络与设备摘要。
- 保证高权限管理操作不能在没有审计记录的情况下成功。
- 支持按配置期限自动清理，不提供人工删除单条日志。

### 2.2 系统设置

- 支持配置注册模式、密码规则、用户名修改冷却期、邀请码默认值和日志保留期限。
- 提供短信、头像存储、管理员二次验证等运行能力的只读状态。
- 配置变更具有类型校验、范围校验、乐观锁和审计记录。
- 向注册页提供最小化的公开策略，使页面与后端实际规则一致。

## 3. 非目标

本轮不做：

- 不记录用户保险箱记录的创建、查看、复制、编辑和删除。
- 不记录保险箱记录名称、平台、渠道、标签或任何解密内容。
- 不提供日志修改、单条删除和手工清空。
- 不提供日志 CSV/Excel 导出。
- 不对接外部 SIEM、邮件、短信或钉钉告警。
- 不实现管理员 TOTP；只展示当前能力状态。
- 不在线修改数据库连接、加密密钥、短信/COS密钥、Cookie安全属性和底层会话配置。
- 不提供“输入任意配置键和值”的通用配置页面。

## 4. 用户与权限

| 角色 | 权限 |
| --- | --- |
| `ROLE_ADMIN` | 查询安全日志、查看日志详情、读取和修改白名单系统设置 |
| `ROLE_USER` | 无权访问管理端日志与设置接口 |
| 匿名用户 | 仅可读取最小化注册策略 |
| `SYSTEM` | 写入定时清理、启动检查等系统事件，不具备管理页面登录能力 |

管理端所有接口继续使用 `/api/admin/**` 的角色校验。前端路由守卫只负责用户体验，不能替代后端鉴权。

## 5. 安全日志产品设计

### 5.1 页面结构

页面路由：`/admin/security-logs`

页面由四部分组成：

1. **真实统计区**
   - 最近24小时登录失败数。
   - 最近7天高风险事件数。
   - 最近7天管理员操作数。
   - 最近24小时被拦截事件数。
2. **筛选区**
   - 时间：最近24小时、7天、30天、自定义。
   - 分类：认证、账户、会话、邀请码、公告、模板、设置、系统。
   - 结果：成功、失败、已拦截。
   - 风险：普通、注意、高风险。
   - 主体类型：个人用户、管理员、匿名、系统。
   - 搜索：事件编号、管理员/用户名、目标标识；不支持搜索手机号明文。
3. **日志列表**
   - 时间。
   - 风险。
   - 事件。
   - 操作主体。
   - 操作目标。
   - 结果。
   - 来源摘要。
   - 详情入口。
4. **详情任务层**
   - 展示经过白名单过滤的结构化元数据。
   - 不展示请求体、响应体、Token、Cookie或原始User-Agent。

手机端列表转为摘要卡，详情采用全屏任务层；平板与桌面使用表格和右侧详情层。

### 5.2 风险等级

| 等级 | 枚举 | 示例 |
| --- | --- | --- |
| 普通 | `INFO` | 登录成功、创建邀请码、发布公告 |
| 注意 | `WARNING` | 登录失败、密码重置、查看邀请码明文、会话失效 |
| 高风险 | `HIGH` | 多次失败后拦截、禁用用户、降低日志保留期限、关闭注册 |

风险等级由服务端事件字典决定，前端不能自行修改。

### 5.3 结果

| 枚举 | 中文 |
| --- | --- |
| `SUCCESS` | 成功 |
| `FAILED` | 失败 |
| `BLOCKED` | 已拦截 |

失败原因只保存稳定错误码，例如 `BAD_CREDENTIALS`、`SMS_CODE_INVALID`，不保存任意异常文本和堆栈。

### 5.4 第一批事件字典

#### 认证与注册

| 事件编码 | 中文名称 | 风险 | 记录目标 |
| --- | --- | --- | --- |
| `USER_LOGIN_SUCCEEDED` | 个人用户登录成功 | `INFO` | 用户 |
| `USER_LOGIN_FAILED` | 个人用户登录失败 | `WARNING` | 脱敏登录标识 |
| `USER_LOGOUT_SUCCEEDED` | 个人用户退出登录 | `INFO` | 用户 |
| `ADMIN_LOGIN_SUCCEEDED` | 管理员登录成功 | `INFO` | 管理员 |
| `ADMIN_LOGIN_FAILED` | 管理员登录失败 | `WARNING` | 脱敏管理员标识 |
| `ADMIN_LOGOUT_SUCCEEDED` | 管理员退出登录 | `INFO` | 管理员 |
| `USER_REGISTERED` | 用户注册成功 | `INFO` | 用户 |
| `USER_REGISTRATION_FAILED` | 用户注册失败 | `WARNING` | 脱敏手机号或用户名 |
| `SMS_CODE_SEND_BLOCKED` | 验证码发送被限流 | `WARNING` | 脱敏手机号 |
| `PASSWORD_RESET_SUCCEEDED` | 登录密码重置成功 | `WARNING` | 用户 |
| `PASSWORD_RESET_FAILED` | 登录密码重置失败 | `WARNING` | 脱敏手机号 |

高频普通参数校验失败不进入安全日志，避免把安全日志变成接口错误日志。只有认证失败、限流、拦截和身份安全事件进入。

#### 用户与会话管理

| 事件编码 | 中文名称 | 风险 |
| --- | --- | --- |
| `USER_DISABLED_BY_ADMIN` | 管理员禁用用户 | `HIGH` |
| `USER_ENABLED_BY_ADMIN` | 管理员启用用户 | `WARNING` |
| `USER_SESSIONS_REVOKED_BY_ADMIN` | 管理员使用户会话失效 | `WARNING` |
| `USERNAME_CHANGED` | 用户修改用户名 | `INFO` |
| `USER_AVATAR_CHANGED` | 用户修改头像 | `INFO` |

用户名变更只记录新旧用户名的脱敏摘要或哈希，不记录头像URL。

#### 邀请码

| 事件编码 | 中文名称 | 风险 |
| --- | --- | --- |
| `INVITATION_CREATED` | 创建邀请码 | `INFO` |
| `INVITATION_DELETED` | 删除邀请码 | `WARNING` |
| `INVITATION_PLAIN_CODE_VIEWED` | 查看邀请码明文 | `WARNING` |
| `INVITATION_REDEEMED` | 邀请码被使用 | `INFO` |

日志只保存邀请码ID与 `codeHint`，禁止保存完整邀请码。

#### 公告与系统模板

| 事件编码 | 中文名称 | 风险 |
| --- | --- | --- |
| `ANNOUNCEMENT_CREATED` | 创建公告草稿 | `INFO` |
| `ANNOUNCEMENT_UPDATED` | 修改公告 | `INFO` |
| `ANNOUNCEMENT_PUBLISHED` | 发布公告 | `WARNING` |
| `ANNOUNCEMENT_OFFLINED` | 下线公告 | `WARNING` |
| `SYSTEM_TEMPLATE_CREATED` | 创建系统模板 | `INFO` |
| `SYSTEM_TEMPLATE_UPDATED` | 修改系统模板 | `INFO` |
| `SYSTEM_TEMPLATE_PUBLISHED` | 发布系统模板 | `WARNING` |
| `SYSTEM_TEMPLATE_OFFLINED` | 下线系统模板 | `WARNING` |
| `SYSTEM_TEMPLATE_DELETED` | 删除系统模板草稿 | `WARNING` |

公告日志可保存公告ID和标题；模板日志可保存模板ID、模板名称和状态，但不得保存模板示例字段值。

#### 系统设置与系统任务

| 事件编码 | 中文名称 | 风险 |
| --- | --- | --- |
| `SYSTEM_SETTINGS_UPDATED` | 修改系统设置 | 由变更项决定 |
| `SECURITY_LOG_RETENTION_CHANGED` | 修改日志保留期限 | 降低期限为 `HIGH` |
| `SECURITY_LOG_PURGE_SUCCEEDED` | 安全日志定时清理完成 | `INFO` |
| `SECURITY_LOG_PURGE_FAILED` | 安全日志定时清理失败 | `HIGH` |
| `AUTH_FAILURES_AGGREGATED` | 高频认证失败已聚合 | `HIGH` |

### 5.5 日志字段

表：`security_audit_event`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 事件ID |
| `occurred_at` | `DATETIME(6)` | 事件发生时间，UTC |
| `category` | `VARCHAR(32)` | 事件分类 |
| `event_type` | `VARCHAR(64)` | 稳定事件编码 |
| `risk_level` | `VARCHAR(16)` | `INFO/WARNING/HIGH` |
| `result` | `VARCHAR(16)` | `SUCCESS/FAILED/BLOCKED` |
| `actor_type` | `VARCHAR(16)` | `USER/ADMIN/ANONYMOUS/SYSTEM` |
| `actor_id` | `CHAR(36) NULL` | 已认证主体ID |
| `actor_label_snapshot` | `VARCHAR(64) NULL` | 当时用户名快照 |
| `identifier_hint` | `VARCHAR(64) NULL` | 匿名失败事件的脱敏标识 |
| `identifier_hash` | `CHAR(64) NULL` | 登录标识HMAC，用于关联攻击 |
| `target_type` | `VARCHAR(32) NULL` | 用户、邀请码、公告、模板、设置等 |
| `target_id` | `VARCHAR(64) NULL` | 目标ID或设置键 |
| `target_label_snapshot` | `VARCHAR(128) NULL` | 安全的目标摘要 |
| `error_code` | `VARCHAR(64) NULL` | 稳定错误码 |
| `request_id` | `VARCHAR(64) NULL` | 请求关联ID |
| `route_template` | `VARCHAR(128) NULL` | 路由模板，不含查询参数 |
| `http_method` | `VARCHAR(8) NULL` | 请求方法 |
| `ip_masked` | `VARCHAR(64) NULL` | 掩码IP |
| `ip_fingerprint` | `CHAR(64) NULL` | IP的HMAC指纹 |
| `browser_family` | `VARCHAR(32) NULL` | 浏览器族 |
| `os_family` | `VARCHAR(32) NULL` | 操作系统族 |
| `device_type` | `VARCHAR(16) NULL` | 桌面、手机、平板、未知 |
| `occurrence_count` | `INT` | 聚合次数，普通事件为1 |
| `metadata_json` | `JSON NULL` | 事件专属白名单元数据 |

索引：

- `(occurred_at DESC)`
- `(event_type, occurred_at DESC)`
- `(risk_level, occurred_at DESC)`
- `(result, occurred_at DESC)`
- `(actor_type, actor_id, occurred_at DESC)`
- `(identifier_hash, occurred_at DESC)`
- `(target_type, target_id, occurred_at DESC)`

不为保险箱用户数据建立任何关联或索引。

### 5.6 元数据白名单

每种事件在代码中定义可写元数据字段。例如：

```json
{
  "sessionsRevoked": 2,
  "previousStatus": "ACTIVE",
  "newStatus": "DISABLED"
}
```

禁止进入 `metadata_json`：

- 密码、短信验证码、密保答案。
- Cookie、Session ID、CSRF Token、Authorization。
- 原始手机号、完整IP、完整User-Agent。
- 邀请码明文。
- 保险箱账号、密码、记录名称、备注、平台、渠道、标签和动态字段。
- 请求体、响应体、异常堆栈和数据库SQL。

未知元数据键直接拒绝写入，不做“尽量过滤后保存”。

### 5.7 客户端来源解析

新增统一 `ClientContextResolver`：

- 默认使用 `request.getRemoteAddr()`。
- 只有直接来源属于配置的受信代理时，才读取 `Forwarded` 或 `X-Forwarded-For`。
- IPv4展示为网段掩码，例如 `124.222.9.*`。
- IPv6只显示前64位网段摘要。
- 使用部署环境注入的 `AUDIT_FINGERPRINT_KEY` 对IP与匿名登录标识做HMAC-SHA256。
- User-Agent只解析为浏览器族、操作系统族、设备类型，不保存原文。

禁止继续由各控制器自行解析 `X-Forwarded-For`。

### 5.8 写入一致性

新增公共接口：

```java
public interface SecurityAuditRecorder {
    void record(SecurityAuditEvent event);
    void recordIndependent(SecurityAuditEvent event);
}
```

规则：

- 管理员关键写操作在原业务事务中写入日志；写入失败则业务事务回滚。
- 登录成功、注册成功和密码重置成功与对应业务更新放在同一事务。
- 登录失败、短信限流等没有业务事务的事件使用 `REQUIRES_NEW` 独立事务。
- 记录失败事件后继续抛出原业务异常，不改变现有接口错误码。
- 认证失败审计写入异常时只输出经过脱敏的应用错误日志，不能把原本的401变成500。
- 普通分页查询不写审计日志，避免递归和无意义数据膨胀。
- 安全日志导出、清理策略变更等未来高权限操作必须被记录。

推荐接入点：

| 场景 | 接入位置 |
| --- | --- |
| 个人用户登录成功/失败 | `AuthController` 的认证调用前后 |
| 个人用户退出 | Spring Security `logoutSuccessHandler` |
| 管理员登录/退出 | `AdminAuthController` |
| 注册与密码重置 | 对应应用服务事务 |
| 用户、邀请码、公告、模板写操作 | 对应应用服务事务 |
| 系统设置 | `SystemSettingService` 保存事务 |

### 5.9 保留与清理

- 默认保留180天。
- 可配置为90、180、365天。
- 每天03:30执行一次清理任务。
- 每批最多删除5000条，避免长事务。
- 清理完成后记录删除数量与截止时间，不记录已删除事件内容。
- 清理失败写入应用错误日志，并尝试记录 `SECURITY_LOG_PURGE_FAILED`。
- 管理后台不提供“立即清空”和“删除单条”功能。

### 5.10 高频事件防洪

认证失败不能无限写满日志表：

- 以 `ip_fingerprint + identifier_hash + event_type` 为聚合键。
- 同一聚合键每分钟前5次写独立事件。
- 超出部分不再逐条落库，在时间窗结束时写一条 `AUTH_FAILURES_AGGREGATED`，并用 `occurrence_count` 记录总次数。
- 聚合器只保存HMAC指纹和计数，不保存原始标识。
- 管理员登录失败不采样；达到阈值后仍需保留高风险聚合事件。
- 聚合器异常时回退为限速写入，不得阻塞认证接口。

## 6. 安全日志 API

所有接口要求 `ROLE_ADMIN`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/v1/security-logs` | 分页查询 |
| `GET` | `/api/admin/v1/security-logs/{id}` | 查看单条详情 |
| `GET` | `/api/admin/v1/security-logs/stats` | 获取真实统计 |
| `GET` | `/api/admin/v1/security-logs/event-types` | 获取事件字典与筛选选项 |

分页参数：

```text
from
to
category
eventType
riskLevel
result
actorType
q
page=0
size=20
```

约束：

- `size` 最大100。
- 默认查询最近7天。
- 自定义单次查询跨度最大180天。
- `q` 最大64字符，只匹配事件ID、主体快照和目标安全摘要。
- 响应不返回 `identifier_hash`、`ip_fingerprint` 等内部关联字段，只返回关联结果或掩码值。

## 7. 系统设置产品设计

### 7.1 页面结构

页面路由：`/admin/settings`

页面分为：

1. **注册与账号**
2. **邀请码默认值**
3. **安全日志**
4. **运行能力**

前三组可编辑；运行能力只读。

每组独立保存。离开页面前如存在未保存修改，需要提示。保存前服务端返回变更影响；高影响操作必须使用 `OsConfirmDialog`。

### 7.2 可编辑设置

#### 注册与账号

| 设置键 | 类型 | 默认值 | 允许值/范围 | 生效 |
| --- | --- | --- | --- | --- |
| `registration.mode` | 枚举 | `SMS_VERIFIED` | `CLOSED/SMS_VERIFIED/INVITE_AND_SMS` | 立即 |
| `registration.password_min_length` | 整数 | `8` | 8–32 | 立即，仅新注册和新密码 |
| `profile.username_change_cooldown_days` | 整数 | `30` | 7–180 | 立即 |

注册模式：

- `CLOSED`：关闭新用户注册，同时禁止发送 `REGISTER` 用途短信验证码。
- `SMS_VERIFIED`：维持当前手机号短信验证注册。
- `INVITE_AND_SMS`：短信验证和有效邀请码同时满足后才允许注册。

`INVITE_AND_SMS` 的实现前提：

- 注册请求恢复 `inviteCode` 字段。
- 注册页面读取公开策略后显示邀请码。
- 用户创建与邀请码消费必须处于同一事务。
- 不允许仅靠前端显示/隐藏控制邀请码要求。

密码最小长度：

- 数据层和DTO继续保留8位硬下限。
- 动态策略只能提高要求，不能低于8位。
- 修改后不强制现有用户立即修改密码。
- 注册、密码重置和前端密码规则必须读取同一有效策略。

#### 邀请码默认值

| 设置键 | 类型 | 默认值 | 允许值/范围 | 生效 |
| --- | --- | --- | --- | --- |
| `invitation.default_valid_days` | 整数 | `7` | 1–90 | 仅新建邀请码表单 |
| `invitation.default_max_uses` | 整数 | `1` | 1–1000 | 仅新建邀请码表单 |

这些设置只是默认值，不修改现有邀请码，也不突破服务端最大范围校验。

#### 安全日志

| 设置键 | 类型 | 默认值 | 允许值 | 生效 |
| --- | --- | --- | --- | --- |
| `security.audit_retention_days` | 枚举整数 | `180` | 90、180、365 | 下次清理任务 |

将期限从较大值调低属于高风险操作，确认弹窗必须明确展示：

- 当前期限。
- 新期限。
- 下次清理后可能永久删除的历史范围。
- 不可恢复说明。

### 7.3 只读运行能力

运行能力由后端根据启动配置返回状态，不返回真实配置值：

| 能力 | 示例状态 |
| --- | --- |
| 短信服务 | 可用 / 未配置 / 启动检查失败 |
| 头像存储 | 本地开发存储 / 腾讯云COS可用 / 未配置 |
| 管理员二次验证 | 尚未实现 / 已启用 |
| 安全Cookie | 已要求HTTPS / 本地开发模式 |
| 会话超时 | 60分钟 |
| 安全日志写入 | 正常 / 异常 |

禁止返回：

- Access Key、Secret、Bucket完整名称。
- 数据库地址、用户名和密码。
- 加密密钥、Key ID历史值。
- Cookie内容和Session ID。
- 服务器文件绝对路径。

### 7.4 永远不可在线编辑的配置

- 数据库与Flyway配置。
- 保险箱加密密钥和邀请码加密密钥。
- 短信/COS访问密钥、签名和模板凭据。
- Session Cookie安全属性。
- Actuator暴露范围。
- CORS、CSRF和权限规则。
- 日志级别、日志文件位置。
- 服务器端口、域名和反向代理。

这些配置继续由生产环境变量和部署文件管理。

## 8. 系统设置数据模型

表：`system_setting`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `setting_key` | `VARCHAR(96)` | 主键，只允许代码白名单中的键 |
| `value_json` | `JSON` | 经过类型校验的值 |
| `value_type` | `VARCHAR(16)` | `STRING/INTEGER/BOOLEAN/ENUM` |
| `updated_by_admin_id` | `CHAR(36)` | 最后修改管理员 |
| `updated_at` | `DATETIME(6)` | 更新时间 |
| `version` | `BIGINT` | 乐观锁 |

设置定义不全部入库，而是在代码中维护：

```java
SystemSettingDefinition(
    key,
    group,
    type,
    defaultValue,
    allowedValues,
    min,
    max,
    editable,
    effectMode,
    riskLevel
)
```

读取规则：

1. 从代码定义取得默认值和校验规则。
2. 从数据库读取同键覆盖值。
3. 数据库出现未知键时忽略并产生系统告警，不向业务暴露。
4. 更新时必须再次通过代码定义校验。

## 9. 系统设置 API

### 9.1 管理端

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/v1/system-settings` | 返回分组设置、当前值、默认值、版本和运行能力 |
| `POST` | `/api/admin/v1/system-settings/validate` | 校验变更并返回影响说明，不落库 |
| `PUT` | `/api/admin/v1/system-settings` | 原子保存一组变更 |

更新请求：

```json
{
  "changes": [
    {
      "key": "registration.mode",
      "value": "CLOSED",
      "expectedVersion": 2
    }
  ]
}
```

校验响应：

```json
{
  "valid": true,
  "highestRisk": "HIGH",
  "effects": [
    "新用户将立即无法注册",
    "注册用途短信验证码将停止发送"
  ],
  "confirmationTitle": "确认关闭用户注册？"
}
```

规则：

- 一次只允许提交同一页面分组的设置。
- 所有变更在一个事务内完成。
- 任一配置校验失败或版本冲突时全部回滚。
- 保存成功写入 `SYSTEM_SETTINGS_UPDATED`，日志只记录白名单旧值和新值。

### 9.2 公开注册策略

无需登录：

`GET /api/auth/registration-policy`

响应：

```json
{
  "registrationEnabled": true,
  "mode": "SMS_VERIFIED",
  "smsRequired": true,
  "inviteRequired": false,
  "passwordMinLength": 8
}
```

该接口只用于页面展示。注册服务必须独立读取有效策略并重新校验。

`SecurityConfig` 需要显式允许匿名 `GET /api/auth/registration-policy`，其他系统设置接口仍保持 `ROLE_ADMIN`。

## 10. 配置生效与缓存

- 使用 `SystemSettingRegistry` 维护定义和默认值。
- 使用 `SystemSettingService` 提供类型化读取。
- 本地缓存最长30秒。
- 当前实例保存成功后立即失效本地缓存。
- 多实例部署通过短TTL最终同步；后续可增加Redis广播。
- 冷启动读取失败时，服务不应悄悄采用可能更宽松的策略：
  - 注册模式读取失败时按 `CLOSED` 处理。
  - 密码规则读取失败时至少执行代码硬下限8位。
  - 日志保留设置读取失败时不执行清理任务。

## 11. 前端交互规范

### 11.1 安全日志

- 只显示真实统计，不伪造“系统安全”或“API正常”状态。
- 风险和结果使用文本、图标及语义色共同表达，不只依赖颜色。
- 空状态区分“时间范围内无事件”和“筛选无结果”。
- 加载失败提供重新加载。
- 日志详情不提供复制敏感指纹的按钮。

### 11.2 系统设置

- 复用项目紧凑控件：工作区按钮30px、下拉字段约34px、选项32px。
- 保存失败使用 `useOsToast().error(...)`。
- 保存成功提示“系统设置已更新”，不在提示中回显敏感配置。
- `CLOSED`、`INVITE_AND_SMS`、降低日志保留期限等高影响变更使用 `OsConfirmDialog`。
- 确认弹窗按钮28px，并遵循 `prefers-reduced-motion`。
- 运行能力使用只读状态卡，不伪装为可编辑表单。

## 12. 后端模块建议

```text
com.godlei.onlinesafe.audit
├─ application
│  ├─ SecurityAuditRecorder
│  ├─ SecurityAuditQueryService
│  ├─ SecurityAuditRetentionJob
│  └─ AuditMetadataPolicy
├─ domain
│  ├─ SecurityAuditEvent
│  ├─ AuditCategory
│  ├─ AuditRiskLevel
│  └─ AuditResult
├─ infrastructure
│  ├─ SecurityAuditEventRepository
│  ├─ ClientContextResolver
│  └─ AuditFingerprintService
└─ web
   └─ SecurityAuditAdminController

com.godlei.onlinesafe.settings
├─ application
│  ├─ SystemSettingService
│  ├─ SystemSettingRegistry
│  └─ SystemSettingCapabilityService
├─ domain
│  └─ SystemSetting
├─ infrastructure
│  └─ SystemSettingRepository
└─ web
   ├─ SystemSettingAdminController
   └─ RegistrationPolicyController
```

## 13. 数据库迁移

建议拆为两次迁移：

- `V16__create_security_audit_event.sql`
- `V17__create_system_setting.sql`

迁移继续遵循项目现有MySQL可重复执行约定：

- `CREATE TABLE IF NOT EXISTS`
- 索引创建前查询 `information_schema`
- 不依赖MySQL DDL事务回滚

默认设置不强制插入数据库；没有覆盖值时直接使用代码默认值，避免“迁移默认值”和“代码默认值”发生双重事实源。

## 14. 测试与验收

### 14.1 后端

- 普通用户访问日志或设置接口返回403。
- 用户/管理员登录成功和失败产生正确事件。
- 禁用用户、使会话失效、查看邀请码明文均产生事件。
- 管理操作审计写入失败时，原业务操作回滚。
- 审计日志中不存在密码、验证码、完整邀请码、完整IP和原始User-Agent。
- 未知元数据键被拒绝。
- 日志保留任务按批次清理，读取配置失败时不清理。
- 设置类型、范围、枚举和乐观锁校验通过。
- 注册关闭时，注册与注册短信发送均被后端拒绝。
- 邀请码+短信模式下，用户创建与邀请码消费保持事务一致。
- 公开策略接口不泄露内部配置。

### 14.2 前端

- 390、768、1440三档无页面级横向溢出。
- 手机安全日志为摘要卡，详情层可完整滚动。
- 筛选、分页、空状态、错误重试可用。
- 设置未保存离开时出现提示。
- 高风险变更使用 `OsConfirmDialog`，项目中无新增 `window.confirm`。
- 保存失败使用Toast，不出现弹窗顶部红色横幅。
- 键盘焦点顺序、可见焦点和减少动效通过。

### 14.3 安全验证

- 使用包含密码、验证码、Token、Cookie、邀请码和保险箱字段的测试请求，确认数据库安全日志不包含这些值。
- 伪造 `X-Forwarded-For` 时，如果请求不来自受信代理，日志使用真实直连来源。
- 修改请求中的 `actorId`、`adminId` 不得伪造日志主体；主体必须来自认证上下文。
- 日志查询接口不返回内部HMAC指纹。

## 15. 实施分期

### Phase A：安全日志基础

- V16迁移。
- 统一事件模型、元数据白名单和客户端来源解析。
- 接入用户/管理员登录、密码重置、用户禁用/启用、会话失效。
- 完成日志列表、详情和统计页面。

### Phase B：管理操作审计

- 接入邀请码、公告、系统模板和个人中心事件。
- 完成日志保留任务。
- 对安全日志数据库内容执行敏感值扫描测试。

### Phase C：系统设置

- V17迁移与设置定义注册表。
- 完成设置读取、校验、乐观锁、确认与审计。
- 完成注册策略公开接口。
- 动态接入密码最小长度和用户名冷却期。

### Phase D：注册模式联动

- 接入 `CLOSED`。
- 接入 `INVITE_AND_SMS`，恢复注册邀请码字段与事务消费。
- 完成全流程集成测试和三视口前端验收。

## 16. 待确认决策

进入实施前需要确认：

1. 安全日志默认保留180天是否合适。
2. 注册模式是否采用 `CLOSED / SMS_VERIFIED / INVITE_AND_SMS` 三档。
3. 是否允许管理员动态提高密码最小长度。
4. 用户名修改冷却期是否允许在7–180天内调整。
5. 第一版安全日志是否明确不提供导出。

本方案推荐默认确认：

- 保留180天。
- 采用三档注册模式。
- 允许管理员将密码最小长度在8–32位之间提高。
- 用户名修改冷却期允许7–180天，默认30天。
- 第一版不提供日志导出。

## 17. 推荐结论

建议按照 `Phase A → Phase B → Phase C → Phase D` 实施。

先让所有高权限操作“可追溯且不泄密”，再开放动态策略。不要先做系统设置页面再补审计，否则管理员对全局策略的修改会缺少最关键的追踪能力。
