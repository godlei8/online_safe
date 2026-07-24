# 登录设备与会话管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将个人会话从「同账号最多 1 个」升级为「有限多设备并存 + 用户自主管理」，并交付 `/vault/security` 登录设备页；管理员仍保持最多 1 会话。

**Architecture:** 不新增业务表；活跃会话与设备摘要落在 Spring Session（`SPRING_SESSION` + Session 属性）。统一 `UserSessionService` 负责查询/失效；登录策略拆为 `userSessionAuthenticationStrategy` / `adminSessionAuthenticationStrategy`；个人登录动态读取 `security.max_active_user_sessions`（默认 2，范围 1–10）。

**Tech Stack:** Spring Session JDBC、Spring Security Concurrent Session、现有 `ClientContextResolver` / `SystemSettingRegistry` / `SecurityAuditService`、Vue 3 + Pinia + Vuetify、`OsConfirmDialog` / `useOsToast`。

**Spec:** `docs/superpowers/specs/2026-07-24-device-session-management-design.md`

**已确认决策（按规格 §25）：** 覆盖单会话互踢；默认上限 2；管理员可配 1–10；达上限挤最久未用；改用户名退出其他设备。

---

## File map

### Backend（新建）

| 路径 | 职责 |
| --- | --- |
| `session/domain/SessionMetadataKeys.java` | Session 属性键常量 |
| `session/domain/UserSessionView.java` | 会话视图模型 |
| `session/application/SessionLimitService.java` | 读有效上限 |
| `session/application/SessionMetadataService.java` | 登录后写元数据；缺省补全 publicId |
| `session/application/UserSessionService.java` | 列表/踢单台/踢其他/踢全部/统计/按主体删除 |
| `session/web/UserSessionController.java` | `/api/v1/security/sessions*` |
| `session/web/UserSessionListResponse.java` / `UserSessionResponse.java` | DTO |
| `security/UserConcurrentSessionControlAuthenticationStrategy.java` | 动态 max sessions |

### Backend（修改）

| 路径 | 变更 |
| --- | --- |
| `security/SessionConfig.java` | 拆分 user/admin 策略 bean |
| `auth/web/AuthController.java` | 注入 user 策略 + 写元数据；超限审计 |
| `admin/web/AdminAuthController.java` | 注入 admin 策略 |
| `security/SecurityConfig.java` | `maximumSessions` 与 user 策略对齐（或仅靠自定义策略 + ConcurrentSessionFilter） |
| `settings/application/SystemSettingRegistry.java` | 注册 `security.max_active_user_sessions` |
| `settings/application/SystemSettingService.java` | `maxActiveUserSessions()` |
| `audit/domain/AuditEventType.java` + `SecurityAuditService` | 新事件 |
| `admin/application/UserManagementService.java` | 统计/吊销改走 `UserSessionService` |
| `auth/application/PasswordResetService.java` | 同上 |
| `auth/application/UserProfileService.java` | 改名保留当前、踢其他 |
| `admin/infrastructure/UserSessionRepository.java` | 逐步薄封装或委托，避免新 SQL 分叉 |

### Frontend（新建）

| 路径 | 职责 |
| --- | --- |
| `api/userSessions.ts` | 会话 API |
| `views/vault/VaultSecurityView.vue` | 安全中心 · 登录设备 |
| `composables/useAuthBroadcast.ts` | BroadcastChannel / storage 回退 |

### Frontend（修改）

| 路径 | 变更 |
| --- | --- |
| `router/index.ts` | `/vault/security` |
| `layouts/VaultLayout.vue` | 启用「安全设置」导航与头像菜单「安全中心」 |
| `api/client.ts` | 可见性/周期校验可选挂钩；跨标签清理已有 401 路径补广播 |
| `views/admin/AdminSettingsView.vue` | 展示新设置键（若分组驱动自动出现则只调文案） |
| `stores/auth.ts` / `vault` | 退出全部时清理；广播触发清理 |

### Tests

| 路径 | 覆盖 |
| --- | --- |
| `SessionIsolationIntegrationTest`（扩展或新建 `UserSessionManagementIntegrationTest`） | 多会话、超限、踢设备、越权、管理员仍单会话、改名、审计 |

**无 Flyway：** MVP 不建新表（规格 §19.1）。

---

## Phase A — 会话服务与多设备策略

### Task A1: 系统设置 `security.max_active_user_sessions`

**Files:**
- Modify: `SystemSettingRegistry.java`, `SystemSettingService.java`
- Modify: `AdminSettingsView.vue`（若需新分组元数据）
- Test: 现有系统设置集成测试或轻量单测

- [ ] 注册 `intSetting`：默认 2、范围 1–10、`GROUP` 建议挂 `ACCOUNT` 或新建 `SECURITY`（推荐 `ACCOUNT` 旁「安全与会话」语义：用 `GROUP_ACCOUNT` 或新增 `GROUP_SESSION`；**采用 `GROUP_ACCOUNT` 以免前端多改一组**，标签「个人最大活跃会话数」）
- [ ] `SystemSettingService.maxActiveUserSessions()`：读缓存，非法值回退 2
- [ ] 管理端设置页确认可读写；保存后下次登录生效（无需踢现有）

### Task A2: Session 元数据与统一服务骨架

**Files:**
- Create: `session/domain/*`, `SessionMetadataService`, `SessionLimitService`, `UserSessionService`（先实现 list/count/deleteByPrincipal/expireOldest）
- Modify: `AuthController` 登录成功后写元数据

- [ ] 定义 `SessionMetadataKeys`（publicId、browserFamily、osFamily、deviceType、ipMasked、loginAt）
- [ ] `SessionMetadataService.writeOnLogin(request, session)`：复用 `ClientContextResolver`；只写 String
- [ ] `UserSessionService.listActiveForPrincipal(username, currentSessionId)`：经 `FindByIndexNameSessionRepository`，过滤过期与 expired SessionInformation
- [ ] `countActive` / `deleteAllByPrincipal` 供管理端与密码重置复用
- [ ] 登录成功（fixation 之后）调用 `writeOnLogin`

### Task A3: 拆分个人/管理员认证策略

**Files:**
- Create: `UserConcurrentSessionControlAuthenticationStrategy.java`
- Modify: `SessionConfig.java`, `AuthController.java`, `AdminAuthController.java`, `SecurityConfig.java`

- [ ] `UserConcurrentSessionControlAuthenticationStrategy`：每次 `onAuthentication` 从 `SessionLimitService` 读上限；`exceptionIfMaximumExceeded=false`；挤最久未用（沿用 ConcurrentSessionControl 语义）
- [ ] Bean：`userSessionAuthenticationStrategy`（动态上限 + changeId + register）
- [ ] Bean：`adminSessionAuthenticationStrategy`（固定 1 + changeId + register）
- [ ] AuthController / AdminAuthController 分别注入
- [ ] SecurityConfig：`ConcurrentSessionFilter` / `maximumSessions` 与用户动态上限协调——推荐自定义策略负责登录挤号，Filter 侧 `maximumSessions(-1)` 或足够大并依赖 SessionRegistry 过期标记；**验收**：旧会话请求返回 `SESSION_REPLACED`
- [ ] 超限挤掉时记 `USER_SESSION_LIMIT_REPLACED`（metadata `maxSessions`）

### Task A4: Phase A 集成测试

**Files:**
- Create/Modify: `UserSessionManagementIntegrationTest.java`（或扩展 `SessionIsolationIntegrationTest`）

- [ ] 同一用户可保持 2 个会话（默认）
- [ ] 设为 2 时第 3 次登录成功，最旧会话 401 `SESSION_REPLACED`
- [ ] 管理员仍只能 1 会话
- [ ] 个人/管理 Cookie 互不影响

---

## Phase B — 用户设备管理 API 与页面

### Task B1: 会话 API

**Files:**
- Create: `UserSessionController` + DTO
- Modify: `SecurityConfig`（`/api/v1/**` 已要求 ROLE_USER，无需额外放行）
- Modify: `UserSessionService` 完整实现

- [ ] `GET /api/v1/security/sessions`：排序当前优先；补全缺失 publicId
- [ ] `DELETE /api/v1/security/sessions/{publicId}`：当前会话 → 409 `CURRENT_SESSION_USE_LOGOUT`；其他 → 204；越权/不存在 → 204
- [ ] `POST .../revoke-others` → `{ revokedCount }`
- [ ] 响应不含内部 session id / 完整 IP / UA
- [ ] 审计：`USER_SESSION_REVOKED`、`USER_OTHER_SESSIONS_REVOKED`

### Task B2: 前端安全中心页

**Files:**
- Create: `api/userSessions.ts`, `VaultSecurityView.vue`
- Modify: `router/index.ts`, `VaultLayout.vue`

- [ ] 路由 `/vault/security`，启用侧栏/抽屉「安全设置」，头像菜单「安全中心」
- [ ] 页面：标题、说明、当前会话卡、其他列表、退出其他
- [ ] 状态：加载中 / 失败重试 / 仅当前设备文案 / 多设备
- [ ] 单设备退出、退出其他：`OsConfirmDialog` warning
- [ ] 当前设备退出：复用 `/api/auth/logout`
- [ ] Toast 成功/失败；按钮高度符合 Design.md

### Task B3: Phase B 测试

- [ ] API 集成：列表、踢单台、踢其他、越权 204、当前会话 409
- [ ] 前端手工：390/768/1440 无横溢

---

## Phase C — 退出全部与前端同步

### Task C1: 退出全部 API

- [ ] `POST /api/v1/security/sessions/revoke-all`：踢其他 + invalidate 当前 + 清 USER cookie；204
- [ ] 先抓主体快照再失效；独立事务审计 `USER_ALL_SESSIONS_REVOKED`
- [ ] 前端 danger 确认弹窗；成功清理 Pinia 并跳转登录

### Task C2: 跨标签与周期校验

**Files:**
- Create: `composables/useAuthBroadcast.ts`
- Modify: `App.vue` 或 `VaultLayout.vue`, `api/client.ts`

- [ ] 频道 `online-safe-auth`：`LOGOUT` / `SESSION_REVOKED`（无敏感字段）
- [ ] 退出/401 时广播；其他标签清理并跳登录
- [ ] `visibilitychange` 可见时校验 `/api/auth/session`；活动页每 5 分钟轻量校验

---

## Phase D — 一致性收口

### Task D1: 改用户名 / 密码重置 / 管理端统计

- [ ] `UserProfileService.changeUsername`：保留当前会话，踢其他；刷新 principal；toast「用户名已更新，其他设备已退出」；审计
- [ ] `PasswordResetService` / `UserManagementService` 改用 `UserSessionService`
- [ ] 管理端活跃会话数改为「未过期活跃」计数，与用户列表同源

### Task D2: 全量验收

- [ ] 跑规格 §21 后端清单
- [ ] 更新 `docs/project_notes/decisions.md`、`key_facts.md`
- [ ] 提交推送（用户明确要求时）

---

## 建议执行顺序

1. A1 → A2 → A3 → A4（可先合入，产品行为即变为默认 2 设备）
2. B1 → B2 → B3
3. C1 → C2
4. D1 → D2

每完成一 Phase 跑相关集成测试；不在用户未要求时擅自 push。

## 风险与注意

- **SecurityConfig.maximumSessions(1)** 与动态用户上限冲突：必须一并改掉，否则 Filter 仍按 1 挤号。
- **principal = username**：改名必须踢其他，否则索引分裂。
- **MockMvc 测 Cookie 会话**：需挂 `springSessionRepositoryFilter`（参考 `SessionIsolationIntegrationTest`）。
- 管理端设置降低上限不立刻踢人（规格明确）。
