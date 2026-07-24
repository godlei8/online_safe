# 架构与交互决策

## 2026-07-24 — 管理端手机列表无限滚动

### 背景

手机 Web 不适合页码分页；用户习惯连续上滑浏览。

### 决策

1. ≤599px：隐藏 `v-pagination`，列表滚动到底自动追加下一页（`useMobileInfiniteScroll`）。
2. 底栏展示「共 N 条，已加载 M」与加载状态；桌面端仍用分页器。
3. 筛选/刷新会重置到第 1 页并替换列表。

### 后果

- 用户 / 邀请码 / 公告 / 模板 / 安全日志均已接入。
- 保险箱记录本身为客户端全量列表，无需分页。

## 2026-07-24 — 管理端手机 Web 紧凑布局

### 背景

管理端在 ≤599px 视口首屏几乎被顶栏副标题、统计卡、长提示条与竖排筛选按钮占满，列表数据需大幅滚动才可见；安全日志详情抽屉向 `width` 传入 `'100%'` 字符串无效，表现为右半屏空白。

### 决策

1. 手机端隐藏顶栏副标题，压缩统计卡与筛选区；`OsHintBar` 默认收起。
2. 筛选工具按钮统一包在 `admin-filter-actions`，同一行并排，视觉高度约 `--os-control-field`（34px）。
3. 摘要卡用 `data-mobile-hide` 隐藏非关键列；操作列横排。
4. 安全日志详情：手机端改为全屏 `v-dialog`，桌面保留右侧抽屉。

### 后果

- 列表页首屏目标：统计/筛选后至少可见 1 条摘要卡。
- 新增管理列表页需遵循上述 class 与属性约定。

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
