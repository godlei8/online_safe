# 系统模板设计（MVP / 档位 A）

日期：2026-07-23

## 1. 目标

管理员维护一套**不含真实账密**的系统模板；所有个人用户可浏览已发布模板，并据此新建保险箱记录。修改或下线系统模板**不得**破坏用户已保存记录。

本轮范围锁定为计划档位 **A（推荐 MVP）**。

## 2. 已锁定决策

| 项 | 决策 |
|---|---|
| 范围档位 | **A**：管理端 CRUD + 发布/下线 + 排序；用户端系统/私人分区展示；新建可选用系统模板 |
| 存储 | **明文 JSON**（模板无敏感值，对齐公告模式，不套 AES） |
| 平台 / 渠道 | **平台必填**；**渠道名、渠道网址可选**（空字符串合法） |
| 种子数据 | **本轮不预置** Flyway 种子；上线后由管理员手工创建 |
| 本轮不做 | 系统模板「复制为私人模板」、管理端「复制模板」、从记录反推保存为私人模板的增强、模板版本 diff 合并 |

## 3. 产品规则

1. **状态**：`DRAFT`（草稿）/ `PUBLISHED`（已发布）/ `OFFLINE`（已下线）。
2. **对用户可见**：仅 `PUBLISHED`。草稿与已下线对用户 API 不可见。
3. **内容约束**：模板只保存字段结构（名称、类型、必填、敏感、可复制、hint、顺序、`systemKey`）；**所有字段 `value` 存库前强制清空**。不得作为真实账密示例库。
4. **固定字段**：保存时服务端与前端均走与记录一致的凭证字段归一化思路——确保存在「账号」「密码」系统字段语义（`systemKey=account|password`）；账号类型允许 `TEXT` / `EMAIL` / `PHONE`，密码固定 `PASSWORD`。
5. **已发布可直接编辑**：更新已发布模板**不**强制回草稿（与公告不同：模板无已读弹窗语义）。改动对后续「选用创建」立即生效；**不影响**已创建记录及其 `templateSnapshot`。
6. **下线**：`PUBLISHED → OFFLINE`；用户侧列表消失；历史记录快照保留。
7. **重新发布**：`DRAFT` 或 `OFFLINE` → `PUBLISHED`。
8. **删除**：仅允许删除 `DRAFT`；已发布/已下线只能下线，不可物理删除（避免误删运营资产）。
9. **排序**：`sortOrder` 升序；同序按 `updatedAt` 降序。管理端支持批量或单条调整排序。
10. **选用创建**：用户选择系统模板后，前端生成记录草稿（预填 platform/channel/channelUrl/fields，值均为空），并写入 `templateSnapshot`；用户可继续改字段后再保存。
11. **私人模板并存**：用户模板页分「系统模板」「我的模板」两区；私人模板现有加密 CRUD 不变。

## 4. 数据模型

### 4.1 表 `system_template`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | CHAR(36) PK | UUID |
| `name` | VARCHAR(128) | 模板名称 |
| `platform` | VARCHAR(128) | 所属平台（必填） |
| `channel` | VARCHAR(128) | 渠道名，可空串 |
| `channel_url` | VARCHAR(512) | 渠道网址，可空串 |
| `payload_json` | JSON / LONGTEXT | 结构见下；**不含真实 value** |
| `status` | VARCHAR(16) | `DRAFT` / `PUBLISHED` / `OFFLINE` |
| `sort_order` | INT | 默认 0 |
| `created_by_admin_id` | CHAR(36) | 创建管理员 |
| `updated_by_admin_id` | CHAR(36) | 最后更新管理员 |
| `published_at` | TIMESTAMP NULL | 最近一次发布成功时间 |
| `created_at` / `updated_at` | TIMESTAMP | |
| `version` | BIGINT | 乐观锁 / revision |

索引建议：`(status, sort_order)`；可选 `(platform)`。

Flyway：`V13__create_system_template.sql`（`CREATE TABLE IF NOT EXISTS`）。

### 4.2 `payload_json` 形状

与前端 `PrivateTemplatePayload` 对齐（可复用 schema 名 `SystemTemplatePayload` 别名同一结构）：

```json
{
  "name": "字符串",
  "platform": "字符串",
  "channel": "字符串",
  "channelUrl": "字符串",
  "fields": [
    {
      "id": "uuid",
      "name": "账号",
      "type": "TEXT|PASSWORD|EMAIL|URL|PHONE",
      "required": true,
      "sensitive": false,
      "copyable": true,
      "hint": "",
      "order": 0,
      "systemKey": "account"
    }
  ]
}
```

说明：

- 顶层 `name` / `platform` / `channel` / `channelUrl` 与表列冗余一份，便于列表查询不解析 JSON；**以表列为权威展示字段**，保存时同步写回 JSON。
- `fields[].value` 若传入则忽略并置空。

### 4.3 记录侧 `templateSnapshot`

创建记录时写入（已有字段，扩展约定）：

```json
{
  "source": "SYSTEM",
  "templateId": "uuid",
  "name": "模板名",
  "fields": [ /* 选用当时的字段结构快照 */ ]
}
```

私人模板选用时 `source` 为 `PRIVATE`（兼容旧快照：无 `source` 时前端按私人处理）。

## 5. API

写请求均需 CSRF；管理端需 `ROLE_ADMIN`，用户端需 `ROLE_USER`。

### 5.1 管理端 `/api/admin/v1/system-templates`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 分页列表；查询参数：`status`、`platform`（模糊可选）、`q`（名称模糊可选） |
| GET | `/{id}` | 详情（任意状态） |
| POST | `/` | 创建，默认 `DRAFT`；body 含 name/platform/channel/channelUrl/fields/sortOrder? |
| PUT | `/{id}` | 更新元数据与 fields（任意状态可改；已发布立即对用户生效） |
| POST | `/{id}/publish` | → `PUBLISHED`，写 `publishedAt` |
| POST | `/{id}/offline` | 仅 `PUBLISHED` → `OFFLINE` |
| PATCH | `/{id}/sort` | body `{ "sortOrder": number }` |
| DELETE | `/{id}` | 仅 `DRAFT` 可删 |

错误码（中文 message）：

- `SYSTEM_TEMPLATE_NOT_FOUND`
- `SYSTEM_TEMPLATE_INVALID_STATUS`（如非草稿删除、非已发布下线）
- `VALIDATION_FAILED`（平台/名称空、fields 非法等）

### 5.2 用户端 `/api/v1/system-templates`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 全部 `PUBLISHED` 列表（按 sortOrder、updatedAt）；可选 `platform`、`q` |
| GET | `/{id}` | 仅已发布详情；否则 404 |

无用户侧写接口。

## 6. 后端模块结构（建议）

对齐公告包风格，新建：

```
com.godlei.onlinesafe.systemtemplate
  domain/SystemTemplate.java
  domain/SystemTemplateStatus.java
  infrastructure/SystemTemplateRepository.java
  application/SystemTemplateService.java
  application/...Exception.java
  web/SystemTemplateAdminController.java
  web/SystemTemplateUserController.java
  web/*Request/*Response
```

校验：名称/平台非空白；fields 为数组；归一化 account/password；清空 value。

集成测试：管理端状态机 + 用户仅见已发布 + 下线后 404。

## 7. 前端

### 7.1 管理端 `/admin/templates`

替换占位页为 `AdminTemplatesView`：

- 列表：状态筛选、平台/关键词、排序值展示
- 编辑弹窗/页：名称、平台、渠道、渠道网址、动态字段编辑（复用保险箱字段类型：文本/密码/邮箱/网址/手机号）
- 操作：保存草稿、发布、下线、删除（草稿）、调整排序
- 视觉：沿用管理端公告页密度与 Design.md 令牌

### 7.2 用户端 `/vault/templates`

扩展 `VaultTemplatesView`：

- 分区：**系统模板**（只读卡片 +「使用此模板」）、**我的模板**（现有 CRUD）
- 「使用此模板」→ `editor.openCreate({ templateId, templateSource: 'system' })`

### 7.3 新建记录桥接

- [`useVaultItemEditor`](frontend/src/composables/useVaultItemEditor.ts) 增加 `templateSource: 'private' | 'system' | null`
- [`vault` store](frontend/src/stores/vault.ts) 增加 `loadSystemTemplates` / `buildPayloadFromSystemTemplate`；快照带 `source: 'SYSTEM'`
- 私人模板路径快照补 `source: 'PRIVATE'`（向后兼容）

### 7.4 API 客户端

新增 `frontend/src/api/systemTemplates.ts`（或并入 admin/vault API 模块），管理端与用户端分路径。

## 8. 安全与边界

- 系统模板明文可读对所有登录用户开放（仅结构），**不得**写入真实密钥示例。
- 管理员仍无法通过模板接口读取用户保险箱记录明文。
- 本轮不做物理删除已发布模板、不做定向可见范围、不做富文本说明页。

## 9. 验收标准

1. 管理员可创建草稿并发布；用户列表立即可见。
2. 用户选用系统模板新建记录，字段与平台/渠道预填正确，保存后 `templateSnapshot.source=SYSTEM`。
3. 管理员修改已发布模板字段后，新选用看到新结构；旧记录字段与快照不变。
4. 下线后用户列表不可见，详情 404；旧记录仍可打开。
5. 草稿可删；已发布不可删，仅可下线。
6. 私人模板区行为与现网一致。
7. `mvn test` 与前端 typecheck/build 通过。

## 10. 后续（非本轮）

- 用户「复制为私人模板」
- 管理端复制模板
- 从记录记录保存为私人模板
- 按平台/渠道智能推荐模板（PRD 7.4 逐步筛选）
- Flyway 预置常用平台种子
