# 智能导入（AI 辅助）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付保险箱「智能导入」：上传 xlsx/xls/csv/md/txt → 确定性抽取 → OpenAI 兼容 AI 映射与打分 → 摘要页（N/M/K）确认 → 强制 reauth 后逐条新建入库。

**Architecture:** 新建 `vaultimport` 后端包：会话表存短 TTL 密封候选 JSON；解析器按格式产出 `RawRow`；`OpenAiCompatibleChatClient` 为唯一模型出口（配置 base-url/api-key/model）；规则引擎做阈值分桶与重复检测；commit 逐条调用 `VaultItemService.create`，单条失败不回滚已成功。前端全屏/宽弹层任务流，入口在保险箱首页。

**Tech Stack:** Spring Boot 4 / JPA / Flyway、Apache POI（Excel）、现有 `VaultPayloadCipher` / `RecentReauthenticationService` / `SystemSettingRegistry`、Vue 3 + Vuetify、`OsConfirmDialog` / `useOsToast`。

**Spec:** `docs/superpowers/specs/2026-07-28-smart-import-ai-design.md`

**已锁定决策：** 方案 2；强制 reauth；commit 逐条；OpenAI 兼容可配置模型；重复仅「跳过/仍要新建」；候选短 TTL 密封 JSON。

## Global Constraints

- 中文优先（文案、错误、审计标签、提交说明）。
- 危险操作使用 `OsConfirmDialog`；工作区按钮 30px；手机遵循 Design §7.1。
- 应用日志与审计**禁止**文件正文、单元格值、模型请求/响应正文、候选 payload。
- 管理员不可读导入明文。
- 智能导入 ≠ `.osvault` 恢复。

---

## File map

### Backend（新建）

| 路径 | 职责 |
| --- | --- |
| `resources/db/migration/V19__create_vault_import_session.sql` | 导入会话表 |
| `vaultimport/domain/VaultImportSession.java` | 实体 |
| `vaultimport/domain/ImportSessionStatus.java` | 状态枚举 |
| `vaultimport/domain/ImportFileFormat.java` | 格式枚举 |
| `vaultimport/domain/ImportCandidateBucket.java` | READY / NEEDS_REVIEW / SKIPPED |
| `vaultimport/domain/ImportIssueCode.java` | 问题码 |
| `vaultimport/infrastructure/VaultImportSessionRepository.java` | 仓储 |
| `vaultimport/application/ImportDocument.java` + `RawRow.java` | 抽取中间态 |
| `vaultimport/application/ImportCandidate.java` | 候选 DTO（内存/密封前） |
| `vaultimport/application/ImportFileExtractor.java` | 按格式分发 |
| `vaultimport/application/CsvImportExtractor.java` | CSV |
| `vaultimport/application/ExcelImportExtractor.java` | XLSX/XLS |
| `vaultimport/application/MarkdownImportExtractor.java` | MD |
| `vaultimport/application/TextImportExtractor.java` | TXT |
| `vaultimport/application/OpenAiCompatibleChatClient.java` | OpenAI 兼容 HTTP 客户端 |
| `vaultimport/application/ImportAiMapper.java` | 调模型做映射+置信度 |
| `vaultimport/application/ImportRuleEngine.java` | 阈值、必填、重复、分桶 |
| `vaultimport/application/ImportCandidateSealService.java` | 密封/解封候选 JSON |
| `vaultimport/application/VaultImportService.java` | 会话生命周期 + commit |
| `vaultimport/application/VaultImportRetentionJob.java` | 过期清理 |
| `vaultimport/application/VaultImportException.java` | 业务异常 |
| `vaultimport/web/VaultImportController.java` | `/api/v1/vault/import/**` |
| `vaultimport/web/*Request.java` / `*Response.java` | DTO |

### Backend（修改）

| 路径 | 变更 |
| --- | --- |
| `pom.xml` | 增加 Apache POI（若尚未有） |
| `application.yml` + `deploy/.env.example` | `app.import.ai.*`；multipart 升至 ≥5MB |
| `settings/.../SystemSettingRegistry.java` | 导入相关设置键（`GROUP_DATA_SECURITY`） |
| `settings/.../SystemSettingService.java` | 读取封装 |
| `audit/domain/AuditEventType.java` (+ Category 若需) | 导入审计事件 |
| `security/SecurityConfig.java` | 放行已认证的 import API（若按路径细分） |
| `common/web/GlobalExceptionHandler.java` | 映射 `VaultImportException` |

### Frontend（新建）

| 路径 | 职责 |
| --- | --- |
| `api/vaultImport.ts` | 导入 API 客户端 |
| `components/vault/VaultImportDialog.vue` | 全屏/宽弹层任务流 |
| `components/vault/VaultImportSummary.vue` | N/M/K 摘要与列表 |

### Frontend（修改）

| 路径 | 变更 |
| --- | --- |
| `views/VaultHomeView.vue` | 「导入」入口；挂载对话框 |
| `views/admin/AdminSettingsView.vue` | 展示新设置（若分组驱动则核对文案） |
| `docs/Design.md` / `decisions.md` / `key_facts.md` | 记录约定 |

### Tests

| 路径 | 覆盖 |
| --- | --- |
| `vaultimport/application/*ExtractorTest` | CSV/MD/TXT 抽取 |
| `vaultimport/application/ImportRuleEngineTest` | 分桶与重复 |
| `vaultimport/application/OpenAiCompatibleChatClientTest` | MockWebServer 兼容调用 |
| `vaultimport/VaultImportIntegrationTest` | 上传→摘要→reauth→逐条 commit；开关关闭；超限 |

---

## Phase A — 会话表、设置、异常与抽取

### Task A1: Flyway `vault_import_session` + 实体仓储

**Files:**
- Create: `V19__create_vault_import_session.sql`
- Create: `VaultImportSession` / enums / `VaultImportSessionRepository`

- [ ] 建表字段对齐规格 §7.1，含 `candidates_ciphertext` / `candidates_nonce` / `candidates_algo`（或单列 `candidates_sealed` LONGBLOB + 元数据列），`expires_at`，计数冗余列
- [ ] 实体映射；`ddl-auto: validate` 下启动通过
- [ ] Commit：`feat: 新增智能导入会话表`

### Task A2: 系统设置与应用配置

**Files:**
- Modify: `SystemSettingRegistry.java`, `SystemSettingService.java`
- Modify: `application.yml`, `deploy/.env.example`
- Modify: multipart `max-file-size` / `max-request-size` → `5MB`（或与设置一致）

- [ ] 注册：
  - `feature.vault_smart_import_enabled`（bool，默认 true）
  - `feature.vault_smart_import_confidence_threshold`（可存百分数 int 75 或小数；推荐 int 0–100）
  - `feature.vault_smart_import_max_file_bytes`（默认 5242880）
  - `feature.vault_smart_import_max_rows`（默认 200）
  - `feature.vault_smart_import_session_ttl_minutes`（默认 30）
- [ ] 增加：

```yaml
app:
  import:
    ai:
      enabled: ${IMPORT_AI_ENABLED:false}
      base-url: ${IMPORT_AI_BASE_URL:}
      api-key: ${IMPORT_AI_API_KEY:}
      model: ${IMPORT_AI_MODEL:}
      timeout: ${IMPORT_AI_TIMEOUT:60s}
      max-retries: ${IMPORT_AI_MAX_RETRIES:2}
```

- [ ] `SystemSettingService` 提供 `smartImportEnabled()` / `smartImportMaxRows()` 等只读方法
- [ ] Commit：`feat: 注册智能导入系统设置与 AI 配置项`

### Task A3: 业务异常与审计事件

**Files:**
- Create: `VaultImportException.java`
- Modify: `GlobalExceptionHandler.java`, `AuditEventType.java`

- [ ] 错误码：`IMPORT_DISABLED` `IMPORT_FILE_TYPE_UNSUPPORTED` `IMPORT_FILE_TOO_LARGE` `IMPORT_ROW_LIMIT_EXCEEDED` `IMPORT_REAUTH_REQUIRED` `IMPORT_SESSION_EXPIRED` `IMPORT_AI_UNAVAILABLE` `IMPORT_SESSION_NOT_FOUND`
- [ ] 审计类型（元数据白名单仅非敏感键）：`VAULT_IMPORT_CREATED` `VAULT_IMPORT_PARSED` `VAULT_IMPORT_AI_DONE` `VAULT_IMPORT_COMMITTED` `VAULT_IMPORT_FAILED` `VAULT_IMPORT_DISCARDED`
- [ ] Commit：`feat: 智能导入异常码与审计事件`

### Task A4: 确定性文件抽取器

**Files:**
- Create: `ImportDocument` `RawRow` + 四个 Extractor + `ImportFileExtractor`
- Test: `CsvImportExtractorTest` `MarkdownImportExtractorTest` `TextImportExtractorTest`
- `pom.xml`: Apache POI `poi-ooxml`（版本跟随 Boot 管理或显式锁定）

- [ ] CSV：UTF-8，失败再试 GBK；分隔符 `,` / `;` / `\t` 启发式；首行表头
- [ ] Excel：只读第一个非空 sheet；首行表头；跳过全空行
- [ ] MD：优先匹配本系统导出（`##` + `- 平台：` + 字段表）；否则按 `##` 分块
- [ ] TXT：空行分块；`键:值`/`键：值` 解析
- [ ] 超过 `maxRows` 在抽取后抛 `IMPORT_ROW_LIMIT_EXCEEDED`
- [ ] 单测覆盖样例文件（测试资源放 `src/test/resources/import-samples/`）
- [ ] Commit：`feat: 实现智能导入文件确定性抽取`

---

## Phase B — AI 客户端、规则引擎、会话服务 API

### Task B1: OpenAI 兼容 Chat 客户端

**Files:**
- Create: `OpenAiCompatibleChatClient.java` + `@ConfigurationProperties(prefix = "app.import.ai")`
- Test: `OpenAiCompatibleChatClientTest`（MockWebServer）

- [ ] `POST {base-url}/chat/completions`，Header `Authorization: Bearer {api-key}`
- [ ] Body：`model`、`messages`、`response_format: { type: "json_object" }`（若供应商不支持则降级靠提示词约束 JSON）
- [ ] 超时与 `max-retries`；失败抛 `IMPORT_AI_UNAVAILABLE`
- [ ] **禁止**把 request/response body 写入 logger
- [ ] `enabled=false` 或 base-url/api-key/model 为空时：`isConfigured() == false`
- [ ] Commit：`feat: 新增 OpenAI 兼容导入 AI 客户端`

### Task B2: AI 映射器 + 规则引擎

**Files:**
- Create: `ImportAiMapper.java` `ImportRuleEngine.java` `ImportCandidate.java` `ImportIssueCode.java`
- Test: `ImportRuleEngineTest`

- [ ] `ImportAiMapper.map(ImportDocument)`：分批（如每批 ≤30 行）调用模型；解析 JSON → 候选列表；schema 失败标 `AI_OUTPUT_INVALID`
- [ ] AI 未配置：表格场景产出「原始 cells + 空映射」候选，全部 `NEEDS_REVIEW` + issue `AI_UNAVAILABLE`（或 `MANUAL_MAPPING_REQUIRED`）；禁止假装成功
- [ ] `ImportRuleEngine.classify`：
  - 缺 name/platform → `NEEDS_REVIEW`
  - confidence &lt; 阈值 → `LOW_CONFIDENCE`
  - 与现有未删除记录 `(platform+account)` 或 `(name+platform)` 规范化相等 → `DUPLICATE_SUSPECTED`，默认 `duplicateAction=SKIP`（前端可改 CREATE）
  - 分桶：无阻断且高置信且非「默认跳过的重复」→ `READY`；用户已 SKIP → `SKIPPED`；其余 `NEEDS_REVIEW`
- [ ] Commit：`feat: 智能导入 AI 映射与规则分桶`

### Task B3: 候选密封与会话服务核心

**Files:**
- Create: `ImportCandidateSealService.java` `VaultImportService.java` `VaultImportRetentionJob.java`
- Test: 密封往返单测；服务层单测（Mock AI）

- [ ] 密封：将 `List<ImportCandidate>` JSON 用现有密钥环加密写入会话（AAD 绑定 `ownerId|IMPORT_SESSION|sessionId`）
- [ ] `createSession(ownerId, file)`：开关检查 → 大小/类型检查 → 抽取 → AI/降级 → 规则 → 密封保存 → 审计元数据
- [ ] `getSession` / `patchCandidate` / `discard` / `commit`
- [ ] `commit`：**先** `reauthenticationService.requireRecent`；再仅处理 `bucket==READY` 或显式 id 列表；**逐条** `vaultItemService.create`；汇总 `succeeded`/`failed[]`；清空密封列；状态 `COMMITTED`
- [ ] RetentionJob：每小时清理 `expires_at < now` 或已 COMMITTED/DISCARDED 超时行的密封列与行
- [ ] Commit：`feat: 实现智能导入会话服务与过期清理`

### Task B4: REST Controller

**Files:**
- Create: `VaultImportController.java` + DTOs
- Modify: `SecurityConfig`（若需）
- Test: `VaultImportIntegrationTest`（可先 @SpringBootTest + MockMvc，AI Mock Bean）

- [ ] `POST /api/v1/vault/import/sessions` multipart `file`
- [ ] `GET /api/v1/vault/import/sessions/{id}`
- [ ] `PATCH /api/v1/vault/import/sessions/{id}/candidates/{candidateId}`
- [ ] `POST /api/v1/vault/import/sessions/{id}/commit`
- [ ] `DELETE /api/v1/vault/import/sessions/{id}`
- [ ] 越权：其他用户 session → 404/403（统一不存在）
- [ ] 集成测：无 reauth → `IMPORT_REAUTH_REQUIRED`；逐条一成一败；开关关闭
- [ ] Commit：`feat: 暴露智能导入 REST API`

---

## Phase C — 前端任务流与文档

### Task C1: API 客户端 + 导入对话框骨架

**Files:**
- Create: `frontend/src/api/vaultImport.ts`
- Create: `VaultImportDialog.vue`（步骤：选文件 → 解析中 → 摘要 → 结果）
- Modify: `VaultHomeView.vue` 增加 outlined「导入」按钮

- [ ] 隐私说明文案按规格 §5.3
- [ ] 功能关闭时隐藏入口（可读公开设置或尝试上传后处理；推荐首页加载时读 `smartImportEnabled`——若无公开接口，则按钮常显、上传返回 `IMPORT_DISABLED` 时 toast）
- [ ] 手机 `fullscreen`；桌面 `max-width` 约 720–840
- [ ] Commit：`feat: 保险箱增加智能导入入口与任务层骨架`

### Task C2: 摘要页与需处理编辑

**Files:**
- Create: `VaultImportSummary.vue`
- Modify: `VaultImportDialog.vue`

- [ ] 展示 ready / needsReview / skipped 计数
- [ ] Tab「将导入」「需处理」；需处理可改 name/platform/字段、重复动作 SKIP/CREATE
- [ ] 「确认导入 N 条」：若无 reauth，先弹密码 reauth（复用数据安全同款 `POST /api/v1/security/reauth`），再 commit
- [ ] 结果页：成功数 + 失败行（无明文）
- [ ] Commit：`feat: 完成智能导入摘要确认与逐条提交 UI`

### Task C3: 文档与管理端设置可见性

**Files:**
- Modify: `docs/Design.md`（§5.x 智能导入要点）
- Modify: `docs/project_notes/decisions.md`、`key_facts.md`
- Modify: `AdminSettingsView.vue` / AGENTS 若需一句入口说明

- [ ] 记录：OpenAI 兼容可配置、强制 reauth、逐条 commit、与备份恢复区分
- [ ] Commit：`docs: 同步智能导入约定到 Design 与项目记忆`

---

## Phase D — 验收与打磨

### Task D1: 端到端验收清单

- [ ] 本系统 MD 导出回流：高置信可导入
- [ ] 混乱 CSV：多数 READY，少数 NEEDS_REVIEW
- [ ] 重复行：默认不进 N；改 CREATE 后可导入
- [ ] 关开关 / 未配 AI / 超 5MB / 超 200 行：中文错误
- [ ] 日志抽样无明文；审计仅元数据
- [ ] 390 视口全屏可用
- [ ] Commit：`test: 补齐智能导入验收用例`（若有自动化）或文档勾选验收

---

## Spec coverage checklist

| 规格要求 | 任务 |
| --- | --- |
| xlsx/xls/csv/md/txt | A4 |
| 结构化抽取 + AI 映射 | A4, B2 |
| OpenAI 兼容可配置 | A2, B1 |
| 摘要 N/M/K | B3, C2 |
| 重复 SKIP/CREATE | B2, C2 |
| 强制 reauth | B3, C2 |
| 逐条 commit | B3 |
| 短 TTL 密封 + 清理 | A1, B3 |
| 开关与限流 | A2, B3, B4 |
| 不落明文日志 | B1, B3, D1 |
| 前端入口与手机 | C1, C2 |
| 文档 | C3 |

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-28-smart-import-ai.md`.

两种执行方式：

1. **Subagent-Driven（推荐）** — 每任务新开子代理，任务间复查，迭代快  
2. **Inline Execution** — 本会话按 executing-plans 连续执行并设检查点  

要先提交规格+计划文档，还是直接开始按 Phase A 实现？若实现，选 1 还是 2？
