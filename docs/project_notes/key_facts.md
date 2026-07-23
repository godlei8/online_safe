# 关键事实与约定

## UI 统一组件

| 场景 | 组件 / 类名 | 文档 |
| --- | --- | --- |
| 表单录入弹窗（记录/模板） | `os-form-dialog*` | `docs/Design.md` |
| 二次确认（删除/异常/禁用等） | `OsConfirmDialog` / `os-confirm-dialog*` | `docs/Design.md` §5.3.1 |

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

## 确认弹窗变体速查

- `danger`：不可恢复删除
- `warning`：需留意（如标记异常）
- `primary`：正向确认（如启用）

## 相关路径

- 组件：`frontend/src/components/OsConfirmDialog.vue`
- 样式：`frontend/src/styles/main.scss`（搜索 `os-confirm-dialog`）
- 已接入：保险箱删除模板、标记异常；管理端邀请码删除、用户禁用/启用/使会话失效
