# 登录设备与会话管理设计规格

> 状态：方案稿，待确认后进入实施计划  
> 日期：2026-07-24  
> 产品定位：长期信任的账号资产管理工具  
> 用户路由：`/vault/security`  
> API前缀：`/api/v1/security/sessions`

## 1. 背景

项目当前已经具备：

- JDBC Spring Session。
- 个人端与管理端独立Session Cookie。
- 登录成功/失败、退出和管理员会话失效审计。
- 管理员查看用户活跃会话数量并使该用户全部会话失效。
- 前端收到401后清理认证状态和已加载保险箱数据。

但普通用户目前不能查看或管理自己的登录设备。更重要的是，当前系统采用“同一主体最多1个会话”，再次登录会挤掉旧会话。这与产品需求中的多设备访问、指定设备退出和全部设备退出存在冲突。

因此，本模块不是单纯新增一个列表页面，而是需要把会话模型从“单设备互踢”升级为“有限多设备并存、用户自主管理、管理员保留紧缩权限”。

## 2. 产品定义

页面中的“登录设备”实际指一个活跃的浏览器登录会话：

- 同一台电脑的Chrome与Edge算两个会话。
- 同一浏览器多个标签页共享同一Cookie，只算一个会话。
- 清除Cookie或使用无痕模式重新登录会形成新会话。
- 系统不承诺识别物理设备唯一身份。

页面需要明确说明：

> 每个浏览器登录状态会作为一个设备会话显示。同一设备使用不同浏览器时会分别显示。

## 3. 目标

- 普通用户可查看自己的全部活跃会话。
- 清楚标记当前会话。
- 支持退出指定的其他会话。
- 支持退出其他全部会话。
- 支持退出全部会话并返回登录页。
- 达到会话上限时自动退出最久未使用的旧会话。
- 会话失效后及时清理当前浏览器已加载的保险箱明文。
- 所有退出操作接入现有安全日志。
- 不向用户或管理员暴露Cookie、Spring Session ID、完整IP或原始User-Agent。

## 4. 非目标

MVP不做：

- 不使用Canvas、字体列表、硬件信息等浏览器指纹。
- 不把活跃会话标记为“可信设备”。
- 不提供永久信任设备或免验证登录。
- 不保存精确地理位置。
- 不展示完整IP和原始User-Agent。
- 不提供管理员查看用户详细设备列表；管理员继续只看数量并可使全部会话失效。
- 不管理管理员自己的多设备会话；管理员继续保持最多1个会话。
- 不实现WebSocket实时踢下线。
- 不实现新环境短信/邮件提醒。
- 不提供自动锁定和独立保险箱锁定按钮。

## 5. 核心产品决策

### 5.1 并发会话上限

| 主体 | 当前 | 推荐MVP |
| --- | --- | --- |
| 普通用户 | 最多1个 | 默认最多2个，可配置1–10 |
| 管理员 | 最多1个 | 继续最多1个 |

新增白名单系统设置：

```text
security.max_active_user_sessions
```

规则：

- 默认值2。
- 允许范围1–10。
- 修改后对下一次登录立即生效。
- 降低上限时不立即强制退出已有会话；后续新登录时按新上限淘汰最久未使用会话。
- 管理员会话上限不开放在线修改。

### 5.2 达到上限

采用“允许新登录、退出最久未使用会话”：

1. 用户完成账号密码验证。
2. 系统读取当前普通用户会话上限。
3. 活跃会话已达上限时，将最近活动时间最早的旧会话标记为失效。
4. 创建新会话并允许登录。
5. 旧会话下次请求时收到 `SESSION_REPLACED`。
6. 记录安全事件。

不采用“拒绝新登录”，避免用户丢失旧设备后无法进入账号。

### 5.3 修改用户名

Spring Session当前按用户名建立主体索引。多设备模式下，修改用户名会造成其他会话仍索引在旧用户名下。

因此修改用户名成功时：

- 保留当前会话。
- 退出其他所有会话。
- 刷新当前会话中的认证主体。
- 提示用户“用户名已更新，其他设备已退出”。
- 记录用户名变更和其他会话退出事件。

## 6. 页面与入口

### 6.1 路由

新增：

```text
/vault/security
```

使用现有 `VaultLayout`：

- 启用侧栏和移动抽屉中的“安全设置”。
- 个人头像菜单增加“安全中心”入口。
- 页面首版只展示已经实现的登录设备模块，不展示不可点击的密码、二次验证等占位卡片。

### 6.2 页面结构

1. 页面标题：`安全中心`
2. 说明：`查看并管理当前账号的登录设备。`
3. 当前会话卡。
4. 其他活跃会话列表。
5. 页面级操作：
   - `退出其他设备`
   - `退出全部设备`

### 6.3 会话卡字段

每张卡显示：

- 设备图标：桌面、手机、平板或未知。
- 设备名称：例如 `Chrome · Windows`。
- 当前会话标识：`当前设备`。
- 登录时间。
- 最近活动时间。
- 预计失效时间。
- 登录IP网段，例如 `124.222.9.*`。
- 操作按钮。

不显示：

- Cookie或Session ID。
- 完整IP。
- 精确位置。
- 原始User-Agent。
- 用户账号、密码或保险箱数据。

### 6.4 操作

#### 当前会话

- 按钮：`退出当前设备`
- 复用现有 `/api/auth/logout`。
- 成功后立即清理本地数据并进入登录页。

#### 其他单个会话

- 按钮：`退出该设备`
- 使用 `OsConfirmDialog`，`warning` 变体。
- 文案：`该设备需要重新登录后才能继续访问保险箱。`

#### 退出其他设备

- 当前会话保留。
- 使用 `OsConfirmDialog`，`warning` 变体。
- 显示预计退出数量。
- 成功提示：`已退出其他 N 个设备`。

#### 退出全部设备

- 包含当前会话。
- 使用 `OsConfirmDialog`，`danger` 变体。
- 文案明确说明操作完成后当前页面也会退出。
- 成功后清理本地数据并进入登录页。

## 7. 页面状态

必须区分：

- 加载中。
- 加载失败，可重新加载。
- 只有当前设备。
- 当前设备加其他会话。
- 操作执行中。
- 操作成功。
- 会话已在其他地方结束。

不使用“暂无数据”描述只有当前设备的状态，应显示：

> 当前只有这一台登录设备。

## 8. 会话数据来源

MVP不新增会话业务表，直接使用：

- `FindByIndexNameSessionRepository`
- `SpringSessionBackedSessionRegistry`
- `SPRING_SESSION`
- `SPRING_SESSION_ATTRIBUTES`

Spring Session已有字段：

| 字段 | 用途 |
| --- | --- |
| `CREATION_TIME` | 会话创建时间 |
| `LAST_ACCESS_TIME` | 最近活动时间 |
| `MAX_INACTIVE_INTERVAL` | 失效间隔 |
| `EXPIRY_TIME` | 预计失效时间 |
| `PRINCIPAL_NAME` | 当前主体索引 |

活跃会话查询必须过滤：

- 已过期会话。
- 已被并发策略标记失效的会话。
- 不属于当前认证用户的会话。

管理端现有活跃会话统计也应改用相同服务，避免直接 `COUNT(*)` 短暂统计到待清理过期行。

## 9. 会话安全元数据

### 9.1 Session属性

登录成功并完成Session Fixation后，在当前Spring Session中写入简单字符串属性：

| 属性 | 说明 |
| --- | --- |
| `os.session.publicId` | 随机UUID，对外会话ID |
| `os.session.browserFamily` | Chrome、Edge、Safari等 |
| `os.session.osFamily` | Windows、Android、iOS等 |
| `os.session.deviceType` | `DESKTOP/MOBILE/TABLET/UNKNOWN` |
| `os.session.ipMasked` | 掩码IP |
| `os.session.loginAt` | 登录时间，UTC |

要求：

- 复用现有 `ClientContextResolver`。
- 不保存原始User-Agent。
- 不保存完整IP与IP指纹到Session属性。
- 只存 `String` 或基础可序列化类型，避免版本升级导致反序列化失败。
- 旧会话缺失公开ID时，在当前用户首次打开设备页面时补生成并保存。
- 旧会话缺少设备摘要时显示“未知浏览器 · 未知系统”，不伪造。

### 9.2 公开会话ID

API只返回 `os.session.publicId`：

- 不返回内部 `Session.getId()`。
- 不返回 `SPRING_SESSION.PRIMARY_ID` 或 `SESSION_ID`。
- 删除会话时，服务端先按当前用户加载活跃会话，再匹配公开ID。
- 传入其他用户的公开ID时按“目标不存在”处理，不泄露归属。

## 10. 当前会话判断

服务端使用当前请求的内部会话ID，与加载到的Spring Session内部ID比较：

```text
request.getSession(false).getId() == session.getId()
```

比较结果只转换为 `current: true/false` 返回，内部ID不进入响应和日志。

## 11. API设计

所有接口要求 `ROLE_USER`。

### 11.1 查询活跃会话

```http
GET /api/v1/security/sessions
```

响应：

```json
{
  "activeCount": 2,
  "maxActiveSessions": 5,
  "sessions": [
    {
      "id": "5648b44a-b9e7-4de4-a494-3dc02fd50a30",
      "current": true,
      "deviceType": "DESKTOP",
      "browserFamily": "Chrome",
      "osFamily": "Windows",
      "displayName": "Chrome · Windows",
      "ipMasked": "124.222.9.*",
      "loginAt": "2026-07-24T08:00:00Z",
      "lastActiveAt": "2026-07-24T09:12:00Z",
      "expiresAt": "2026-07-24T10:12:00Z"
    }
  ]
}
```

排序：

1. 当前会话。
2. 其他会话按最近活动时间倒序。

### 11.2 退出指定的其他会话

```http
DELETE /api/v1/security/sessions/{publicSessionId}
```

规则：

- 只能操作当前用户自己的活跃会话。
- 如果目标是当前会话，返回409：

```json
{
  "code": "CURRENT_SESSION_USE_LOGOUT",
  "message": "请使用退出登录结束当前设备会话"
}
```

- 目标不存在、已经结束或属于其他用户时统一返回204，保持幂等并避免枚举。
- 成功后返回204。

### 11.3 退出其他全部会话

```http
POST /api/v1/security/sessions/revoke-others
```

响应：

```json
{
  "revokedCount": 2
}
```

当前会话必须保留。

### 11.4 退出全部会话

```http
POST /api/v1/security/sessions/revoke-all
```

规则：

- 包含当前会话。
- 删除其他会话后使当前 `HttpSession` 失效。
- 清除个人端 `ONLINE_SAFE_SESSION` Cookie。
- 返回204。

### 11.5 当前设备退出

继续使用：

```http
POST /api/auth/logout
```

不增加重复接口。

## 12. 错误码

| 错误码 | HTTP | 说明 |
| --- | ---: | --- |
| `CURRENT_SESSION_USE_LOGOUT` | 409 | 不能通过单会话删除接口结束当前会话 |
| `SESSION_LIST_UNAVAILABLE` | 503 | Spring Session仓储暂时不可用 |
| `UNAUTHENTICATED` | 401 | 当前会话已失效 |
| `SESSION_REPLACED` | 401 | 达到并发上限后该旧会话被替换 |

错误响应不得包含内部会话ID、数据库表名或Cookie值。

## 13. 登录流程调整

个人用户登录：

1. 校验账号密码。
2. 用户并发策略读取 `security.max_active_user_sessions`。
3. 达到上限时标记最久未使用会话失效。
4. 执行Session Fixation。
5. 注册当前会话。
6. 写入安全会话元数据。
7. 保存SecurityContext。
8. 更新最近登录时间。
9. 写入登录成功安全日志。
10. 返回登录结果。

管理员登录继续执行固定单会话策略。

## 14. 普通用户与管理员策略拆分

当前普通用户和管理员共用一个 `SessionAuthenticationStrategy`。开放多设备后必须拆分：

```text
userSessionAuthenticationStrategy
adminSessionAuthenticationStrategy
```

### 普通用户

- 动态读取用户最大活跃会话数。
- 默认2。
- 超限时退出最久未使用会话。

### 管理员

- 固定最多1个会话。
- 再次登录继续使旧管理员会话失效。
- 不允许通过后台设置放宽。

推荐新增自定义策略：

```java
UserConcurrentSessionControlAuthenticationStrategy
```

覆盖每次认证的最大会话数读取逻辑，避免只在应用启动时读取设置。

## 15. 后端模块建议

```text
com.godlei.onlinesafe.session
├─ application
│  ├─ UserSessionService
│  ├─ SessionMetadataService
│  └─ SessionLimitService
├─ domain
│  ├─ UserSessionView
│  └─ SessionMetadataKeys
└─ web
   ├─ UserSessionController
   ├─ UserSessionListResponse
   └─ UserSessionResponse
```

现有 `admin.infrastructure.UserSessionRepository` 建议逐步收敛为统一 `UserSessionService`：

- 用户侧查询和单会话失效。
- 管理侧活跃会话数量。
- 管理员使用户全部会话失效。
- 密码重置使全部会话失效。
- 用户名变更时只保留当前会话。

避免多处直接拼接 `SPRING_SESSION` SQL。

## 16. 安全日志联动

扩展现有 `AuditEventType`：

| 事件 | 风险 | 元数据白名单 |
| --- | --- | --- |
| `USER_SESSION_REVOKED` | `WARNING` | `sessionsRevoked` |
| `USER_OTHER_SESSIONS_REVOKED` | `WARNING` | `sessionsRevoked`、`reason` |
| `USER_ALL_SESSIONS_REVOKED` | `HIGH` | `sessionsRevoked` |
| `USER_SESSION_LIMIT_REPLACED` | `WARNING` | `maxSessions` |

规则：

- 日志不记录公开或内部会话ID。
- 日志不记录目标设备完整信息。
- 目标统一为当前用户。
- 用户主动退出全部会话时，先捕获安全主体快照，完成失效后使用独立事务记录。
- 管理员使用户会话失效继续使用现有 `USER_SESSIONS_REVOKED_BY_ADMIN`。

## 17. 前端数据清理与跨标签页

### 17.1 当前标签页

退出或收到401时清理：

- `useVaultStore().clearSessionData()`
- 公告状态。
- 用户认证状态。
- 页面中尚未保存的敏感表单数据。

当前项目使用服务端加密，不再处理客户端DEK。

### 17.2 同浏览器其他标签页

新增无敏感数据广播：

```text
频道：online-safe-auth
消息：LOGOUT / SESSION_REVOKED
```

- 优先使用 `BroadcastChannel`。
- 不支持时使用 `storage` 事件作为回退。
- 消息不包含用户名、用户ID、会话ID或保险箱数据。
- 接收标签页立即清理内存并进入登录页。

### 17.3 远端设备

不使用WebSocket。远端会话在以下时机发现失效：

- 下一次API请求。
- 页面重新切回可见状态时校验 `/api/auth/session`。
- 页面保持活动时每5分钟执行一次轻量校验。

## 18. 前端交互规范

- 复用项目浅蓝视觉体系和安全中心壳层。
- 工作区按钮30px；手机点按区域至少40px。
- 下拉框继续使用紧凑规范。
- 退出指定设备、退出其他设备、退出全部设备使用 `OsConfirmDialog`。
- 保存/请求失败使用 `useOsToast().error(...)`。
- 不使用 `window.confirm`。
- 不显示虚构位置、风险分数或“设备可信”标签。
- 设备类型不能识别时使用“未知设备”，不猜测。
- 列表加载、错误、仅当前设备和多设备状态必须分别呈现。

## 19. 数据库与配置

### 19.1 MVP数据库

不新增Flyway迁移。

原因：

- 活跃会话已经存在Spring Session表中。
- 设备摘要可以保存在Spring Session属性中。
- 会话过期后摘要随Session属性一起删除，符合MVP只管理活跃会话的范围。

### 19.2 系统设置

在现有 `SystemSettingRegistry` 增加：

```text
security.max_active_user_sessions
```

由于 `system_setting` 只保存覆盖值，默认值由代码注册表提供，因此无需数据库种子迁移。

## 20. 安全边界

- 会话查询必须从认证上下文取得当前用户，禁止接受前端 `userId`。
- 单会话退出先按当前用户加载会话，再匹配公开ID。
- 目标不存在与越权目标统一返回204，避免会话枚举。
- 内部Spring Session ID、Cookie值不进入响应、日志、Toast和前端状态。
- 设备摘要来自服务端现有安全解析器。
- 管理员只看活跃数量，不查看用户详细设备环境。
- 会话页面不得读取、展示或记录保险箱内容。
- CSRF继续保护所有退出写操作。

## 21. 测试与验收

### 21.1 后端集成测试

- 同一用户可以同时保持5个会话。
- 第6次登录成功，最久未使用会话收到 `SESSION_REPLACED`。
- 管理员仍然只能保持1个会话。
- 个人和管理员Cookie继续互不影响。
- 用户A不能查看或退出用户B的会话。
- API响应不包含内部Session ID、Cookie、完整IP和原始User-Agent。
- 当前会话正确标记。
- 单个其他会话退出后不能再访问保险箱。
- 退出其他设备保留当前会话。
- 退出全部设备后当前会话也失效。
- 管理员使用户全部会话失效仍然有效。
- 密码重置后全部会话失效。
- 修改用户名后只保留当前会话，并能继续查询新用户名下的会话。
- 过期会话不计入列表和管理员统计。
- 所有退出事件写入安全日志且不包含会话ID。

### 21.2 前端测试

- 当前会话卡和其他会话卡渲染正确。
- 只有当前设备时显示真实空状态。
- 三类退出操作均使用正确确认弹窗。
- 退出其他设备后列表即时刷新。
- 退出全部设备后清空Pinia并跳转登录。
- 401和 `SESSION_REPLACED` 文案正确。
- 跨标签页退出能同步清理。
- 390、768、1440三档无横向溢出。
- 手机操作命中区至少40px。
- 键盘焦点与减少动效通过。

### 21.3 安全测试

- 构造其他用户公开会话ID不能越权退出。
- 随机猜测公开会话ID不泄露存在性。
- 伪造 `X-Forwarded-For` 不会生成伪造IP摘要。
- Session属性中不存在密码、Cookie、保险箱内容和原始User-Agent。
- 日志数据库中不存在公开/内部会话ID。

## 22. 实施分期

### Phase A：会话服务与多设备策略

- 拆分个人/管理员认证策略。
- 普通用户默认2会话，管理员保持1会话。
- 增加动态会话上限设置。
- 写入安全会话元数据。
- 建立统一 `UserSessionService`。

### Phase B：用户设备管理页面

- 新增会话API。
- 启用 `/vault/security` 与导航。
- 完成当前设备、其他会话、单个退出和退出其他设备。

### Phase C：全部退出与前端同步

- 完成退出全部设备。
- 增加跨标签页广播。
- 增加页面可见性和5分钟会话校验。
- 接入安全日志事件。

### Phase D：一致性收口

- 用户名修改退出其他设备。
- 密码重置、管理员会话失效统一复用新服务。
- 修正管理员活跃会话统计。
- 完成多浏览器、三视口和越权测试。

## 23. 后续能力

后续如需“新设备提醒”，应单独建设“已知登录环境”：

- 使用第一方随机设备标识，不使用浏览器指纹。
- 明确告知用途和保留期限。
- 支持用户移除已知设备。
- 与站内通知或短信提醒联动。

该能力需要新的持久数据表和隐私评审，不与MVP活跃会话列表混做。

## 24. 待确认决策

进入实施前需要确认：

1. 是否覆盖当前“同账号最多1会话”的决策。
2. **已确认：普通用户默认最大会话数采用2。**
3. 是否允许管理员在1–10之间配置用户会话上限。
4. 达到上限时是否自动退出最久未使用会话。
5. 修改用户名时是否退出其他设备。

## 25. 推荐结论

本方案建议以上五项全部采用。

对于“长期信任的账号资产管理工具”，多设备访问是基础能力，但必须是有限、可见、可撤销的多设备。保留管理员单会话、用户默认2会话，并让用户随时退出其他设备，是当前项目安全性和可用性之间最合适的平衡。
