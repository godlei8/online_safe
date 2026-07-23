# 系统模板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务实现。步骤使用 checkbox（`- [ ]`）跟踪。

**Goal:** 落地系统模板 MVP：管理端维护明文结构模板（发布/下线/排序），用户端浏览已发布模板并选用创建保险箱记录。

**Architecture:** 新表 `system_template` 存明文 JSON；管理 API 全量状态机，用户 API 只读已发布；前端管理页替换占位，保险箱模板页分区展示；选用时写入带 `source=SYSTEM` 的 `templateSnapshot`。

**Tech Stack:** Spring Boot + Flyway V13、JPA、Vue 3 + Vuetify、现有 CSRF/Session 安全模型

**规格依据：** [`docs/superpowers/specs/2026-07-23-system-templates-design.md`](../specs/2026-07-23-system-templates-design.md)

## Global Constraints

- 中文优先（`AGENTS.md`）
- 模板禁止持久化真实字段值（存库前清空 `value`）
- 平台必填；渠道/渠道网址可选
- 本轮无 Flyway 种子数据
- 本轮不做：复制为私人模板、管理端复制、记录反推私人模板、版本 diff 合并
- 已发布可直接编辑；仅草稿可物理删除

---

## 文件地图

| 区域 | 新建 / 修改 |
|---|---|
| Flyway | 新建 `backend/src/main/resources/db/migration/V13__create_system_template.sql` |
| 后端包 | 新建 `backend/.../systemtemplate/**`（domain / infrastructure / application / web） |
| 安全 | 修改 `SecurityConfig`（若路径未落入现有 `/api/admin/**`、`/api/v1/**` 规则则核对） |
| 异常 | 修改 `GlobalExceptionHandler` 映射新业务异常 |
| 测试 | 新建 `SystemTemplateIntegrationTest`（或等价） |
| 前端 API | 新建 `frontend/src/api/systemTemplates.ts` |
| 前端 store / editor | 修改 `stores/vault.ts`、`composables/useVaultItemEditor.ts`、`VaultItemFormDialog.vue` |
| 用户模板页 | 修改 `views/vault/VaultTemplatesView.vue` |
| 管理模板页 | 替换 `views/admin` 占位为 `AdminTemplatesView.vue`；改 `router` / `AdminLayout` 入口文案 |
| 文档 | 实现后同步根/前后端 README 中「尚未实现：系统模板」表述 |

---

## Task 1：库表 + 领域模型 + 仓储

**交付：** 可编译的实体与仓库，迁移可重复执行。

- [ ] 编写 `V13__create_system_template.sql`：`CREATE TABLE IF NOT EXISTS system_template`（列对齐规格 §4.1），索引 `(status, sort_order)`
- [ ] 新建 `SystemTemplateStatus` 枚举：`DRAFT` / `PUBLISHED` / `OFFLINE`
- [ ] 新建实体 `SystemTemplate`（乐观锁 `version`、工厂 `create`、状态流转方法）
- [ ] 新建 `SystemTemplateRepository`（按 status 分页、用户侧已发布列表查询）
- [ ] 本地或测试 profile 启动验证 Flyway 应用到 V13

---

## Task 2：服务层校验与状态机（TDD）

**交付：** Service + 失败用例先红后绿。

- [ ] 写失败测试：无平台名创建 → `VALIDATION_FAILED`；删除已发布 → `SYSTEM_TEMPLATE_INVALID_STATUS`；用户拉草稿 id → 404
- [ ] 实现 `SystemTemplateService`：
  - 创建默认 `DRAFT`
  - upsert 同步表列与 `payload_json`，清空 fields value，归一化 account/password
  - `publish` / `offline` / `deleteDraft` / `updateSort`
  - `listPublishedForUser` / `getPublishedForUser`
- [ ] 业务异常类 + `GlobalExceptionHandler` 中文错误体
- [ ] 跑通相关测试

---

## Task 3：REST Controllers + 集成测试

**交付：** 管理/用户 HTTP 契约可用。

- [ ] DTO：`SystemTemplateUpsertRequest`、`SystemTemplateAdminResponse`、`SystemTemplateUserResponse`、`SortOrderRequest`
- [ ] `SystemTemplateAdminController`：`/api/admin/v1/system-templates`（GET 分页、GET id、POST、PUT、publish、offline、PATCH sort、DELETE）
- [ ] `SystemTemplateUserController`：`/api/v1/system-templates`（GET 列表、GET id）
- [ ] 集成测试覆盖：发布可见、下线不可见、草稿不可删以外状态、CSRF/角色隔离
- [ ] `.\mvnw.cmd test` 通过

---

## Task 4：前端管理端页面

**交付：** `/admin/templates` 可完整运营模板。

- [ ] 新增 `api/systemTemplates.ts`（admin 方法）
- [ ] 实现 `AdminTemplatesView.vue`：列表筛选、编辑表单（字段类型与保险箱一致含 PHONE）、发布/下线/删除草稿/排序
- [ ] 路由占位组件替换；侧栏「系统模板」指向真实页
- [ ] 总览文案去掉「系统模板将陆续接入」或改为已开放
- [ ] 手动点验：草稿→发布→编辑已发布→下线

---

## Task 5：前端用户端选用创建

**交付：** 模板页双分区 + 系统模板新建记录。

- [ ] `useVaultItemEditor` 增加 `templateSource: 'private' | 'system' | null`
- [ ] `vault` store：`systemTemplates`、`loadSystemTemplates`、`buildPayloadFromSystemTemplate`；私人路径快照补 `source: 'PRIVATE'`
- [ ] `VaultItemFormDialog` 按 `templateSource` 拉系统或私人模板
- [ ] `VaultTemplatesView`：系统模板区（只读 + 使用）+ 我的模板区（原逻辑）
- [ ] 保存一条记录后检查 `templateSnapshot.source === 'SYSTEM'`
- [ ] `npx vue-tsc --noEmit -p tsconfig.app.json` 通过

---

## Task 6：文档与收尾

**交付：** README 与规格一致，无占位残留。

- [ ] 更新根 `README.md`、`frontend/README.md`、`backend/README.md`：系统模板已实现；补管理/用户 API 摘要
- [ ] 自检规格验收标准 §9 清单
- [ ] 提交（中文 commit message）；按需推送

---

## 风险与注意

- 私人模板 ID 与系统模板 ID 均为 UUID，**必须**用 `templateSource` 区分，禁止只靠 ID 猜测。
- 用户侧列表勿返回草稿/下线；管理端详情可返回任意状态。
- JSON 列在 MySQL 8.4 可用 `JSON` 类型；若与现有风格不一致可用 `LONGTEXT` + 应用层校验（与公告正文类似即可）。
