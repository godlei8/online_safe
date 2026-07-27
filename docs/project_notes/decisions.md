# 架构与交互决策

## 2026-07-27 — 数据安全与恢复中心（用户侧 Phase A–C）

### 背景

软删除无法自助恢复；Markdown 导出为明文不适合备份；`key_id` 单钥校验阻碍轮换；需要可验证的本地加密备份与安全合并恢复。

### 决策

1. 采用 `VaultKeyRing`：加密用当前写钥，解密按行 `key_id`；兼容单钥 env，可选密钥环文件。
2. Session 二次验证 10 分钟（`os.security.reauthenticatedAt`），覆盖备份快照、批量恢复、永久删除。
3. 回收站复用 `deleted_at`；保留天数白名单设置 7/30/90；定时清理 04:10。
4. `.osvault`：浏览器 PBKDF2+AES-GCM，独立备份密码；服务端只提供明文快照且 `Cache-Control: no-store`。
5. 恢复仅安全合并；同用户活跃 ID 跳过；跨用户 ID 重分配；批次幂等。
6. 系统灾备 Phase D–E（离站备份、管理端灾备页）另开，本期不做。

### 后果

- 入口：`/vault/security/data`；API：`/api/v1/data-security/**`、`/api/v1/security/reauth`。
- Flyway V18：`data_recovery_operation` / `data_recovery_batch`。

## 2026-07-24 — 手机 Web 布局通则（筛选栏 / 无限滚动 / chrome）

### 背景

≤599px 管理端曾出现：搜索被「创建邀请码」等长按钮挤没；底栏「共 N 条」与「已全部加载」左右两行占位过重；标题区 `flex-basis` 造成大块空白；列表用页码分页不符合上滑习惯。

### 决策

1. **筛选栏两行模型**：第 1 行搜索 + `admin-filter-actions--tools`；第 2 行 `admin-filter-actions--primary`（创建/新建全宽）。搜索 `min-width ≥ 7.5rem`。
2. **次级筛选**：`AdminFilterSheet`；桌面多 `v-select` 在 `xs` 隐藏。
3. **无限滚动**：`useMobileInfiniteScroll`；隐藏 `v-pagination`；底栏单行 `admin-page__infinite-status`（约 11px muted）。
4. **chrome**：藏顶栏副标题、`OsHintBar` 可收起、统计卡压扁、`data-mobile-hide`；区块标题与缩小刷新同行。
5. **详情层**：手机全屏 `v-dialog`，禁止无效 drawer `width` 字符串。
6. 规范写入 `docs/Design.md` §7.1，并在 `AGENTS.md` / `CURSOR.md` 列为硬约束。

### 后果

- 用户 / 邀请码 / 公告 / 模板 / 安全日志与保险箱工具区均按此复用。
- 新增列表页不得再发明 nowrap 挤搜索的横排。

## 2026-07-23 — 统一二次确认弹窗（OsConfirmDialog）

### 背景

删除模板、标记异常、管理端删除邀请码/禁用用户等场景原先混用简易白框或 `window.confirm`，色彩与动效不统一，破坏金融级信任感，也容易在后续功能中再次分叉。

### 决策

1. 新增统一组件 `frontend/src/components/OsConfirmDialog.vue`。
2. 样式与动效集中在 `frontend/src/styles/main.scss` 的 `os-confirm-dialog*`。
3. 语义变体：`danger`（删除等）、`warning`（标记异常等）、`primary`（正向确认如启用）。
4. 后续所有二次确认必须复用该组件；禁止 `window.confirm`。
5. 规范写入 `docs/Design.md` §5.3.1，并在 `CURSOR.md` / `AGENTS.md` 中列为硬约束。

### 后果

- 新增危险操作时优先挂 `OsConfirmDialog`，只改标题/文案/变体。
- 管理端邀请码删除、用户禁用/启用/会话失效已迁移到该组件。
- 需尊重 `prefers-reduced-motion`（已在样式中处理）。

## 2026-07-23 — 下拉框紧凑规范

### 背景

创建邀请码等表单中 `v-select` 展开选项行过高、字号偏大，与已收紧的按钮/输入节奏不一致。

### 决策

1. 全局默认：`VSelect` / `VAutocomplete` / `VCombobox` 使用 `density="compact"`，并挂 `menuProps.contentClass = 'os-select-menu'`。
2. 字段高约 **34px**（`--os-control-field`），选项行高 **32px**（`--os-select-item`），选项标题 13px。
3. 规范写入 `docs/Design.md` §3.4，并同步 `CURSOR.md` / `AGENTS.md` / `key_facts.md`。

### 后果

- 新下拉默认即紧凑，无需逐页改样式。
- 自定义菜单仍应复用 `os-select-menu` 或等价行高。

## 2026-07-23 — 按钮高度整体 −10px

### 背景

确认删除等按钮视觉偏高，用户要求整体收紧，并作为后续统一规范。

### 决策

相对旧规范整体约 −10px：

| 场景 | 旧 | 新 |
| --- | --- | --- |
| 工作区默认 `VBtn` | 40px | **30px** |
| 认证主按钮 | 48px | **38px**（`--os-control-auth`） |
| 确认弹窗按钮 | ≈32–40px | **28px** |
| 小控件令牌 | 36px | **26px** |

写入 `Design.md` §3.3、`CURSOR.md`、`AGENTS.md`、`key_facts.md`。

### 后果

- 新按钮默认跟令牌走，不再写死 40/48。
- 手机触控命中区仍用 `--os-control-touch`（40px）保证点按。

## 2026-07-24 — 个人/管理端双 Session Cookie 与同账号挤掉

### 背景

同一浏览器先登个人再登管理端时，共用 `ONLINE_SAFE_SESSION` 导致个人 SecurityContext 被覆盖，个人会话失效。

### 决策

1. 个人端 Cookie：`ONLINE_SAFE_SESSION`；管理端：`ONLINE_SAFE_ADMIN_SESSION`（`SurfaceAwareCookieHttpSessionIdResolver` 按 `/api/admin/**` 分流）。
2. 管理员固定最多 1 个有效会话；再次登录挤掉旧会话（`SESSION_REPLACED`）。
3. 个人用户默认最多 2 个活跃会话（系统设置 `security.max_active_user_sessions`，范围 1–10）；达上限时允许新登录并挤掉最久未用会话。
4. 管理员 Session 主体名加前缀 `admin:`，避免与个人用户同名冲突。

### 后果

- 同浏览器可同时保持个人保险箱与管理后台登录态。
- 退出管理端不得清除个人 Cookie；退出个人不得清除管理端 Cookie。

## 2026-07-24 — 登录设备与安全中心

### 背景

个人端需可见、可撤销的有限多设备访问；管理员仍保持单会话。

### 决策

1. 不新增业务表；设备摘要写入 Spring Session 属性（publicId、浏览器/系统/设备类型、脱敏 IP、登录时间）。
2. 统一 `UserSessionService` 供用户安全中心、密码重置、管理端吊销/统计复用。
3. API：`GET/DELETE /api/v1/security/sessions*`、`POST .../revoke-others`、`POST .../revoke-all`；对外仅暴露 publicId。
4. 前端 `/vault/security`；危险操作使用 `OsConfirmDialog`；跨标签用 `BroadcastChannel`（`online-safe-auth`）同步退出。
5. 修改用户名：保留当前会话、踢其他设备，并刷新当前 principal 索引。

### 后果

- 降低个人并发上限不会立即踢现有会话，仅影响后续登录。
- 管理端活跃会话计数与用户端同源（未过期活跃）。

## 2026-07-24 — 安全日志与白名单系统设置

### 背景

管理端「安全日志」「系统设置」长期为占位页；需要可追溯高权限操作，且禁止在线改密钥/数据库等部署配置。

### 决策

1. 先审计后设置：Phase A–B 打通事件模型与埋点，再开放 `system_setting` 白名单策略。
2. 管理写操作与审计同事务；认证失败用独立事务，不把 401 变成 500。
3. 注册模式三档；密码最小长度仅可提高到 8–32；用户名冷却 7–180 天（默认 30）。
4. 第一版不做日志导出；清理任务读不到保留期限时跳过删除。
5. 规格：`docs/superpowers/specs/2026-07-24-security-logs-system-settings-design.md`。

### 后果

- 新高权限写操作必须接入 `SecurityAuditService` / `SecurityAuditRecorder`。
- 新可运营策略必须进 `SystemSettingRegistry`，禁止通用键值配置页。
