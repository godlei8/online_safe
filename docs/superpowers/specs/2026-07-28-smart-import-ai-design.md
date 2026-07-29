# 智能导入（AI 辅助）开发规格

> 状态：已定稿，可进入实施计划  
> 日期：2026-07-28  
> 产品定位：批量从杂乱文件迁入账密，AI 只做结构理解与映射，用户在摘要页确认后写入  
> 用户入口：保险箱首页「导入」→ 全屏/宽弹层任务流  
> API 前缀：`/api/v1/vault/import`  
> 管理开关：系统设置组内总开关（可关闭智能导入）  
> 模型：OpenAI 兼容 Chat Completions，base-url / api-key / model 可配置  
> 已定：强制 reauth · commit 逐条 · 候选明文短 TTL 密封 JSON

---

## 1. 背景与目标

### 1.1 现状

- 账密为动态字段模型（`name` / `platform` / `channel` / `fields[]` 等），无法假设固定「账号/密码」两列。
- 已支持 Markdown **明文导出**；加密备份恢复是另一条路径（`.osvault`）。
- PRD P2 已规划 Markdown / CSV / Excel 批量导入；粘贴识别约定为「候选 + 用户确认，不直写」。
- 用户文件来源杂、列名乱、结构不一，手写每种格式解析器不可持续。

### 1.2 目标（第一版）

1. 支持 `.xlsx` / `.xls` / `.csv` / `.md` / `.txt` 上传。
2. 后端**确定性抽取**原始行/块，再由**自有后端调用大模型 API**完成列映射、字段归类与置信度打分。
3. 导入前必须经过**摘要确认页**：展示将导入 N / 需处理 M / 跳过 K；高置信不强制逐条打开。
4. 重复仅**检测提示**，选项为「跳过 / 仍要新建」，不做自动合并或覆盖。
5. 明文可经自有服务器与模型供应商，但**应用日志与审计不得落明文**；功能可被管理端与用户关闭。

### 1.3 非目标（第一版不做）

- 密码管理器专用格式（Bitwarden / 1Password / Chrome CSV 专规）——可作第二版增强。
- 整文件无结构直喂大模型（超上下文、成本与不可控）。
- 异步超大批量任务管道（第一版同步 + 条数上限；超限提示拆分）。
- 导入附件、图片 OCR、多文件打包一次导入。
- 导入时覆盖已有记录或智能字段合并。
- 管理员代用户导入或查看导入明文。

---

## 2. 已锁定的产品决策

| 项 | 决策 |
| --- | --- |
| AI 部署 | 自有后端调大模型 API；明文会经服务器 |
| 模型接入 | **OpenAI 兼容 Chat Completions**；供应商不固定，运维通过配置切换（base-url / api-key / model） |
| 确认 UX | 摘要页确认（N/M/K），高置信可一键写入，不强制逐条打开 |
| 重复 | 仅检测提示；「跳过 / 仍要新建」 |
| 格式 | `.xlsx` `.xls` `.csv` `.md` `.txt` |
| 架构 | **方案 2**：结构化抽取 + AI 只做映射与打分 |
| 写入策略 | 一律**新建**记录（新 ID）；确认前不落库 |
| 提交鉴权 | **强制 reauth**：`commit` 前必须存在有效的近期二次验证窗口 |
| Commit 事务 | **逐条提交**：单条失败不回滚已成功条目，结果页汇总成功/失败 |
| 与备份关系 | 智能导入 ≠ `.osvault` 恢复；文案需区分 |

---

## 3. 用户流程

```text
保险箱首页「导入」
        │
        ▼
选择文件（校验扩展名/大小）
        │
        ▼
上传 → 创建导入会话（IMPORT_SESSION）
        │
        ▼
后端：解析文件 → 原始行/块
        │
        ▼
后端：AI 映射（可分批）+ 规则校验 + 重复检测
        │
        ▼
摘要页
  · 将导入 N（高置信，可展开抽查）
  · 需处理 M（低置信 / 缺字段 / 冲突）→ 可改映射、改动作
  · 跳过 K（用户已标跳过或无法解析）
        │
        ▼
用户确认「导入 N 条」
        │
        ▼
批量创建保险箱记录（服务端加密入库）
        │
        ▼
结果页：成功 / 失败条数；会话销毁明文缓存
```

### 3.1 摘要页规则

- **将导入**：`confidence >= 阈值` 且无阻断错误、重复策略为「新建」或未命中重复。
- **需处理**：低置信、缺名称/平台、字段为空过多、命中疑似重复、列映射冲突等。
- **跳过**：用户在需处理中选择跳过，或解析失败且无法修复的行。
- 确认按钮文案：`确认导入 {N} 条`；N=0 时禁用。
- 需处理行未处理完时：允许仍只导入当前「将导入」集合（默认）；可选「先处理完再导入」开关（默认关，降低批量摩擦）。

### 3.2 手机端

- 遵循 `Design.md` §7.1：导入为**全屏任务层**；摘要列表可无限滚动或分页。
- 危险确认（若有清空候选）使用 `OsConfirmDialog`。

---

## 4. 解析与 AI 职责边界

### 4.1 确定性抽取（不依赖 AI）

| 格式 | 抽取策略 |
| --- | --- |
| CSV | 自动探测分隔符与编码（UTF-8 / GBK 回退）；首行作表头候选；每行 → `RawRow` |
| XLSX/XLS | 仅读**第一个有数据的工作表**；首行表头；空行跳过 |
| MD | 优先识别本系统导出结构（`## 名称` + 元信息列表 + `### 字段` 表格）；否则按 `##`/`---` 分块 |
| TXT | 按空行分块；块内 `键:值` / `键：值` 行优先；否则整块进 `rawText` |

输出统一中间态：

```text
ImportDocument
  sourceFileName, contentType, byteSize, detectedFormat
  sheets? / blocks?
  rows: RawRow[]
    rowIndex
    headers?: string[]          // 表格场景
    cells?: Record<string, string>
    rawText?: string            // 非表格式块
```

### 4.2 AI 只负责

1. 表头 / 键名 → 标准槽位映射：`name` `platform` `channel` `channelUrl` `account` `password` `notes` `expiresAt` `extraFields[]`
2. 推断字段类型：`TEXT` `PASSWORD` `EMAIL` `URL` `PHONE`
3. 给出每条 `confidence`（0–1）与 `issues[]`（机器可读码）
4. **不得**自行决定是否写入、覆盖或删除已有数据

### 4.3 规则引擎（AI 之后）

- Zod / 服务端等价校验：名称、平台非空；字段名非空。
- `ensureCredentialFields` 语义：尽量保证账号/密码系统字段存在（可空值，但结构在）。
- 置信度阈值（建议默认 **0.75**，系统设置可调）。
- 重复检测（第一版）：同用户未删除记录中，`(platform 规范化 + account 规范化)` 相同，或 `(name 规范化 + platform 规范化)` 相同 → 标 `DUPLICATE_SUSPECTED`。
- AI 失败或超时：表格场景降级为「表头人工映射向导」；TXT/MD 无表头则整批进需处理并提示重试。

### 4.4 提示词与输出契约（示意）

系统提示要求：

- 只输出 JSON，符合 schema；禁止复述完整密码到「分析说明」字段。
- 未知列进入 `extraFields`，不要丢弃。
- 对空行返回 `skip: true`。

模型输出经 schema 校验失败则该批标记 `AI_OUTPUT_INVALID`，不写入。

### 4.5 OpenAI 兼容客户端（供应商可配置）

代码只实现 **一套 OpenAI 兼容 HTTP 客户端**（`POST {baseUrl}/chat/completions`，Bearer `api-key`），不绑定某一家厂商 SDK。

运维在部署配置中填写即可切换模型，例如：

| 场景示例 | `base-url` 示意 |
| --- | --- |
| OpenAI 官方 | `https://api.openai.com/v1` |
| 兼容网关 / 中转 | 自建或第三方的 `/v1` 根路径 |
| 国内兼容服务 | 厂商文档给出的 OpenAI 兼容 endpoint |

配置项（环境变量 / `application.yml`，密钥不进仓库、不进前端）：

```yaml
app:
  import:
    ai:
      enabled: true
      base-url: ${IMPORT_AI_BASE_URL:}
      api-key: ${IMPORT_AI_API_KEY:}
      model: ${IMPORT_AI_MODEL:}
      timeout: 60s
      max-retries: 1
```

约束：

1. `base-url`、`api-key`、`model` 均未配置或 `enabled=false` 时：智能映射不可用；表格可走手动列映射降级，否则返回 `IMPORT_AI_UNAVAILABLE`。
2. 请求体使用标准 `messages` + 建议 `response_format: json_object`（若对方不支持则降级为提示词约束 JSON，并加强 schema 校验）。
3. 审计只记 `base-url` 主机名（或配置别名）与 `model`，**不记** api-key、不记 messages 内容。
4. 管理端设置页可展示「是否已配置 AI」（布尔），**禁止**回显完整 api-key；密钥仅能通过服务器环境变量更换。

---

## 5. 安全、隐私与合规

### 5.1 明文边界

```text
浏览器选文件
    │ HTTPS 上传
    ▼
导入会话临时存储（内存优先；必要时加密落临时对象，TTL 短）
    │ 抽取文本/表格
    ▼
大模型 API（供应商侧明文处理，需合同与区域合规评估）
    │ 结构化候选 JSON
    ▼
用户确认
    │
    ▼
VaultPayloadCipher 加密 → MySQL
    │
    ▼
销毁会话明文与临时文件
```

### 5.2 硬性要求

1. **应用日志**：禁止记录文件正文、单元格值、模型请求/响应正文、候选 payload。
2. **审计日志**：只记元数据——`userId`、会话 ID、文件名（可截断）、格式、字节数、候选条数、N/M/K、导入成功/失败数、耗时、模型供应商与模型名、是否降级。事件类型建议：`VAULT_IMPORT_CREATED` `VAULT_IMPORT_PARSED` `VAULT_IMPORT_AI_DONE` `VAULT_IMPORT_COMMITTED` `VAULT_IMPORT_FAILED` `VAULT_IMPORT_DISCARDED`。
3. **管理端总开关**：关闭后 API 返回明确错误；前端隐藏入口。
4. **用户开关**（个人中心或导入页）：关闭后不可发起智能导入（可保留「仅本地规则解析」为后续可选，第一版一并隐藏）。
5. **二次验证（强制）**：`POST .../commit` **必须**校验有效 reauth 窗口（复用数据安全模块 10 分钟）；无效则 `IMPORT_REAUTH_REQUIRED`，前端弹出密码再验证后再提交。创建会话 / 预览候选不强制 reauth。
6. **限流**：每用户每小时会话数、每日导入条数、单文件大小上限（建议 5MB、≤200 条）。
7. **管理员**：不可查看会话明文或下载用户上传文件。

### 5.3 文案义务

导入页必须可见提示：

> 文件将上传至服务器并由 AI 辅助识别字段；内容可能包含账号密码等敏感信息。识别结果需你确认后才会写入保险箱。可在设置中关闭智能导入。

---

## 6. API 设计（草案）

均需登录；前缀 `/api/v1/vault/import`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/sessions` | `multipart/form-data` 上传文件，创建会话并同步完成解析+AI（或返回 `PROCESSING` 若后续扩展异步） |
| `GET` | `/sessions/{id}` | 取会话状态与摘要统计 + 候选列表（分页） |
| `PATCH` | `/sessions/{id}/candidates/{candidateId}` | 改映射、改重复动作（SKIP / CREATE）、改字段 |
| `POST` | `/sessions/{id}/commit` | 确认导入；**强制 reauth**；默认导入全部 `READY` 项；**逐条**调用创建，返回成功/失败明细 |
| `DELETE` | `/sessions/{id}` | 放弃并销毁明文 |

### 6.1 关键响应字段

```json
{
  "sessionId": "...",
  "status": "READY",
  "summary": {
    "readyCount": 12,
    "needsReviewCount": 3,
    "skippedCount": 1,
    "confidenceThreshold": 0.75
  },
  "candidates": [
    {
      "id": "...",
      "rowIndex": 2,
      "confidence": 0.91,
      "bucket": "READY",
      "issues": [],
      "duplicateAction": null,
      "payload": { "name": "...", "platform": "...", "fields": [] }
    }
  ]
}
```

`bucket`：`READY` | `NEEDS_REVIEW` | `SKIPPED`  
`issues` 例：`LOW_CONFIDENCE` `MISSING_NAME` `MISSING_PLATFORM` `DUPLICATE_SUSPECTED` `AI_OUTPUT_INVALID` `EMPTY_ROW`

### 6.2 错误码（业务）

| 码 | 含义 |
| --- | --- |
| `IMPORT_DISABLED` | 管理端或用户关闭 |
| `IMPORT_FILE_TYPE_UNSUPPORTED` | 扩展名/MIME 不支持 |
| `IMPORT_FILE_TOO_LARGE` | 超大小 |
| `IMPORT_ROW_LIMIT_EXCEEDED` | 超条数 |
| `IMPORT_REAUTH_REQUIRED` | 提交前需再验证 |
| `IMPORT_SESSION_EXPIRED` | TTL 到期 |
| `IMPORT_AI_UNAVAILABLE` | 模型失败且无法降级完成 |

---

## 7. 数据模型

### 7.1 导入会话（建议）

表名：`vault_import_session`

| 列 | 说明 |
| --- | --- |
| `id` | ULID/UUID |
| `owner_id` | 用户 |
| `status` | `UPLOADING` `PARSING` `AI_MAPPING` `READY` `COMMITTING` `COMMITTED` `FAILED` `DISCARDED` |
| `file_name` | 原始名 |
| `file_format` | `XLSX` `XLS` `CSV` `MD` `TXT` |
| `byte_size` | |
| `row_count` | |
| `ready_count` / `needs_review_count` / `skipped_count` | 冗余摘要 |
| `model_provider` / `model_name` | 审计用 |
| `error_code` | 可空 |
| `expires_at` | 默认创建后 **30 分钟** |
| `committed_at` | |
| `created_at` / `updated_at` | |

候选数据：**第一版存本表短 TTL 密封 JSON 列**（`candidates_ciphertext` + `candidates_nonce` 等，可用 VaultPayloadCipher 或会话密封），必须：

- 仅会话存活期内可读；
- commit/discard/expire 后立即清空；
- 定时任务扫过期会话清理；
- 明文不进应用日志。

### 7.2 系统设置键

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `feature.vault_smart_import_enabled` | `true` | 总开关 |
| `feature.vault_smart_import_confidence_threshold` | `0.75` | |
| `feature.vault_smart_import_max_file_bytes` | `5242880` | 5MB |
| `feature.vault_smart_import_max_rows` | `200` | |
| `feature.vault_smart_import_session_ttl_minutes` | `30` | |

大模型连接（`deploy/.env` / 应用配置，**OpenAI 兼容**，不进可被随意改的明文展示）：

| 配置 | 说明 |
| --- | --- |
| `app.import.ai.enabled` | 是否启用 AI 映射 |
| `app.import.ai.base-url` | OpenAI 兼容 API 根路径（含 `/v1`） |
| `app.import.ai.api-key` | Bearer Token |
| `app.import.ai.model` | 模型名（由所选服务商决定） |
| `app.import.ai.timeout` | 请求超时 |
| `app.import.ai.max-retries` | 失败重试次数 |

供应商不固定：换家只需改上述配置，**无需改代码**（只要对方兼容 Chat Completions）。

---

## 8. 前端设计要点

### 8.1 入口

- 保险箱首页标题行次操作：「导入」（outlined），与「新增记录」主按钮区分。
- 关闭功能时不展示入口。

### 8.2 任务步

1. 选文件 + 隐私说明  
2. 解析中（进度条，不展示明文预览大段）  
3. 摘要（三计数 + 两个 Tab：将导入 / 需处理）  
4. 结果  

需处理行：可展开编辑名称/平台/字段；重复行单选「跳过 / 仍要新建」。

### 8.3 写入后

- 刷新保险箱列表；Toast：「已导入 N 条」。
- 部分失败时列出失败行号与原因（无明文）。

---

## 9. 与现有模块关系

| 模块 | 关系 |
| --- | --- |
| `VaultItemService.create` | commit 时**逐条**创建并各自提交；单条失败记入结果列表，已成功条目保留 |
| `RecentReauthenticationService` | **commit 强制校验**；无有效窗口则拒绝 |
| OpenAI 兼容客户端 | 唯一模型调用出口；配置驱动 base-url / key / model |
| Markdown 导出 | MD 导入优先兼容本系统导出结构 |
| `.osvault` 备份恢复 | 入口与文案分离；不走导入会话 |
| 审计 | 新事件类型，禁止 payload |

---

## 10. 验收标准

1. 上传本系统导出的 Markdown，摘要中高置信记录可一键导入，字段与导出一致（允许字段 id 重新生成）。
2. 上传列名混乱的 CSV（如「用户名/口令/网站」），AI 映射后多数进「将导入」，少数进「需处理」。
3. 故意制造与已有记录同平台同账号的行，出现在「需处理」，默认不进入 N；选「仍要新建」后可导入第二条。
4. 关闭管理端开关后，上传接口返回 `IMPORT_DISABLED`，前端无入口。
5. 未配置 `base-url`/`api-key`/`model` 或 AI 关闭时，行为符合降级策略，且不误报「导入成功」。
6. `commit` 在无 reauth 窗口时返回 `IMPORT_REAUTH_REQUIRED`；验证通过后可写入。
7. commit 时故意让其中一条校验失败：其余成功写入，结果页同时展示成功数与失败行。
8. 应用日志抽样不含任何密码或账号明文；审计仅有元数据（可含 model 名，不含 key）。
9. 会话过期或用户取消后，临时明文不可再 GET 到。
10. 超过 200 行或 5MB 被拒绝并中文提示。
11. 390 视口下导入为全屏任务层，摘要可完成「需处理」操作与确认导入。

---

## 11. 实施分期建议

| 阶段 | 内容 |
| --- | --- |
| I1 | 会话 API、CSV/XLSX 确定性解析、无 AI 的手动列映射 MVP（可先不上模型） |
| I2 | OpenAI 兼容客户端 + AI 映射、置信度分桶、摘要页 |
| I3 | MD/TXT 抽取、重复检测、**强制 reauth**、逐条 commit、设置开关与审计 |
| I4 | 打磨降级、限流、体验与 Design 对齐 |

> 说明：若希望「第一版就必须有 AI」，可将 I1/I2 合并；仍建议保留无 AI 降级路径。

---

## 12. 实施拍板（已锁定）

1. **候选明文**：第一版存会话表**短 TTL 密封 JSON 列**（可用现有 VaultPayloadCipher 或独立会话密封密钥）；commit / discard / expire 后清空；定时任务扫过期会话。  
2. **模型 endpoint**：运维配置 `app.import.ai.*`，与代码无关；代码只实现 OpenAI 兼容客户端。

---

## 13. 规格自检

- [x] 强制 reauth、逐条 commit、OpenAI 兼容可配置模型已锁定  
- [x] 候选明文短 TTL 密封 JSON 已锁定  
- [x] 与动态字段模型、服务端加密、二次确认、手机 §7.1 无冲突  
- [x] 明确非目标，避免与 `.osvault` 恢复混淆  
- [x] 安全：不落日志、可审计、可关闭已写入  
- [x] 范围：格式、UX、重复策略与架构决策一致  
