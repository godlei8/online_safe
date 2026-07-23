# 架构与交互决策

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
