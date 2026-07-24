# 关键事实与约定

## UI 统一组件

| 场景 | 组件 / 类名 | 文档 |
| --- | --- | --- |
| 表单录入弹窗（记录/模板） | `os-form-dialog*` | `docs/Design.md` |
| 二次确认（删除/异常/禁用等） | `OsConfirmDialog` / `os-confirm-dialog*` | `docs/Design.md` §5.3.1 |
| 表单校验/保存失败提示 | `useOsToast` / `OsToastHost`（独立弹出，勿用弹窗内红条） | `docs/Design.md` §5 |

## 按钮高度（相对旧规范 −10px）

| 令牌 / 场景 | 高度 |
| --- | --- |
| `--os-control-md` / 工作区默认按钮 | 30px |
| `--os-control-sm` | 26px |
| `--os-control-auth` / 认证主按钮 | 38px |
| 确认弹窗按钮 | 28px |
| 管理端表格行内 `admin-row-actions__btn` | 26px（主操作 flat，勿用 tonal） |
| `--os-control-touch`（手机点按） | 40px |

实现：`frontend/src/plugins/vuetify.ts`（`VBtn.height = 30`）、`main.scss` 令牌与 `.auth-submit` / `.os-confirm-dialog__*`。

## 下拉框（紧凑）

| 要素 | 值 |
| --- | --- |
| 字段高 `--os-control-field` | 34px |
| 选项行高 `--os-select-item` | 32px |
| 菜单类名 | `os-select-menu`（Vuetify 默认已挂） |

实现：`frontend/src/plugins/vuetify.ts`（`VSelect`/`VAutocomplete`/`VCombobox` 的 `menuProps`）、`main.scss` 中 `.os-select-menu`；规范见 `docs/Design.md` §3.4。

## 系统模板状态

- 编辑已发布/已下线模板 → 回到 `DRAFT`，用户侧立即不可见，需重新发布。
- 仅改排序不改变发布状态。
- 已创建记录的 `templateSnapshot` 不受模板后续编辑影响。

## 确认弹窗变体速查

- `danger`：不可恢复删除
- `warning`：需留意（如标记异常）
- `primary`：正向确认（如启用）

## 相关路径

- 组件：`frontend/src/components/OsConfirmDialog.vue`
- 样式：`frontend/src/styles/main.scss`（搜索 `os-confirm-dialog`）
- 已接入：保险箱删除模板、标记异常；管理端邀请码删除、用户禁用/启用/使会话失效

## 会话 Cookie（2026-07-24）

| 面 | Cookie | 说明 |
| --- | --- | --- |
| 个人 | `ONLINE_SAFE_SESSION` | `/api/auth/**`、`/api/v1/**` |
| 管理 | `ONLINE_SAFE_ADMIN_SESSION` | `/api/admin/**` |
| 个人并发 | 默认最多 2 会话（可配 1–10） | 键 `security.max_active_user_sessions`；超限挤最久未用 |
| 管理并发 | 固定 1 会话 | 不可配置；跨面互不影响 |

## 登录设备 / 安全中心（2026-07-24）

| 项 | 约定 |
| --- | --- |
| 页面 | `/vault/security`（侧栏「安全设置」、头像「安全中心」） |
| API | `/api/v1/security/sessions`（列表 / 踢单台 / 踢其他 / 踢全部） |
| 对外 ID | Session 属性 `os.session.publicId`（UUID），不暴露内部 Session ID |
| 跨标签 | `BroadcastChannel` 频道 `online-safe-auth`：`LOGOUT` / `SESSION_REVOKED` |
| 周期校验 | 页面可见时 + 每 5 分钟请求 `/api/auth/session` |
| 设置键 | `security.max_active_user_sessions`（系统设置 →「登录会话」分组） |
| 管理端 | 只看活跃会话数 +「使会话失效」；不提供用户设备详情列表；管理员自身固定 1 会话 |

## 安全日志与系统设置（2026-07-24）

| 项 | 约定 |
| --- | --- |
| 路由 | `/admin/security-logs`、`/admin/settings` |
| 审计表 | `security_audit_event`（Flyway V16） |
| 设置表 | `system_setting`（Flyway V17，仅覆盖值；默认在代码注册表） |
| 公开策略 | `GET /api/auth/registration-policy`（匿名） |
| 注册模式 | `CLOSED` / `SMS_VERIFIED` / `INVITE_AND_SMS` |
| 日志保留 | 90 / 180 / 365 天，默认 180；每天 03:30 清理 |
| 指纹密钥 | 环境变量 `AUDIT_FINGERPRINT_KEY` |
| 禁止 | 日志导出、单条删除、保险箱明文、完整 IP/UA/邀请码明文入日志 |
