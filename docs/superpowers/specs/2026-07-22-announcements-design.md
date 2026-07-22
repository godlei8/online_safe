# 公告功能设计（标准版）

日期：2026-07-22

## 目标

管理员可发布多条系统公告；普通用户在保险箱内通过铃铛查看，支持已读；首页展示最新置顶公告；对「最新一条未读」强制弹窗确认。

## 产品规则

1. 公告状态：`DRAFT`（草稿）/ `PUBLISHED`（已发布）/ `OFFLINE`（已下线）。
2. 可选 `startsAt` / `endsAt`；均未填表示永久有效（在已发布前提下）。
3. **对用户可见**：`PUBLISHED` 且当前时间落在有效窗内（`startsAt` 为空或已开始，且 `endsAt` 为空或未结束）。
4. **编辑后需重新发布**：修改非草稿公告会回到 `DRAFT` 并清空 `publishedAt`；用户端在重新发布前看不到改动。
5. **发布**：每次发布都会刷新 `publishedAt` 为当前时间，并清除该公告全部已读记录；强制弹窗取「可见公告中按 `publishedAt` 最新的未读一条」。
6. `pinned`：置顶。首页横幅取「可见且置顶」中按 `publishedAt` 最新的一条；与是否已读无关。
7. 用户铃铛：列出全部可见公告，含已读标记；`unreadCount > 0` 时显示红点。
8. **强制弹窗**：仅弹最新未读（见上）；点「我知道了」标记已读后关闭。遮罩点击与 ESC 不可关闭。
9. 本版不做：物理删除、已读统计、富文本、定向推送、逐条连弹。

## 数据

- `announcement`：标题、正文、状态、置顶、起止时间、`published_at`、创建管理员、时间戳。
- `announcement_read`：`(user_id, announcement_id)` 主键 + `read_at`。

## API

### 管理端 `/api/admin/v1/announcements`

- `GET` 分页，可按 `status` 筛选
- `POST` 创建（默认草稿）
- `PUT /{id}` 更新标题/正文/时间/置顶（草稿与已下线可改；已发布也可改文案与时间）
- `POST /{id}/publish` → 已发布并写入 `publishedAt`（首次发布写当前时间；再次发布保留原 `publishedAt` 若已有）
- `POST /{id}/offline` → 下线

### 用户端 `/api/v1/announcements`

- `GET /inbox` → `{ unreadCount, latestUnread, pinned }`
- `GET /` → 可见列表（含 `read`）
- `POST /{id}/read` → 幂等已读

## 前端

- 管理：`/admin/announcements` 列表与编辑，侧栏入口「公告」。
- 用户：`VaultLayout` 铃铛抽屉 + 强制对话框；`VaultHomeView` 置顶横幅。
