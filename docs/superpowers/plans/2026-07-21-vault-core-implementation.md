# 保险箱核心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务推进。步骤用 checkbox（`- [ ]`）跟踪。
> 用户已批准直接在本会话内联实现；**不要 commit**，除非用户另行要求。

**Goal:** 实现方案一客户端加密的保险箱核心：密钥信封、密文 CRUD、动态字段、私人模板，并接通首页/详情复制与解锁流程。

**Architecture:** 浏览器 libsodium.js 负责 Argon2id + XChaCha20-Poly1305；服务端 `vault` 包只存密文信封与 revision；DEK 仅存 Pinia 内存。

**Tech Stack:** Java 21、Spring Boot 4.1、JPA、Flyway、Vue 3、Pinia、Vuetify 4、libsodium-wrappers、Zod

## Global Constraints

- 中文优先（`AGENTS.md`）
- 明文永不进 API/日志；Controller 不直接 Repository
- `ownerId` 只来自 `AppUserPrincipal.userId()`
- 敏感字段详情默认明文；列表不展示敏感值；单字段一键复制
- Windows 写中文文件用 UTF-8（避免 PowerShell `Set-Content` 破坏编码）
- 规格：`docs/superpowers/specs/2026-07-21-vault-core-design.md`

---

## File Map

| 路径 | 职责 |
| --- | --- |
| `backend/.../db/migration/V8__create_vault_tables.sql` | key-bundle / item / private_template |
| `backend/.../vault/domain/*` | 实体与枚举 |
| `backend/.../vault/infrastructure/*` | Repository（owner 限定） |
| `backend/.../vault/application/*` | Service + 业务异常 |
| `backend/.../vault/web/*` | Controller + DTO |
| `backend/.../common/web/GlobalExceptionHandler.java` | 404/409 映射 |
| `backend/.../VaultFlowIntegrationTest.java` | 集成测试 |
| `frontend/src/crypto/*` | libsodium 封装 |
| `frontend/src/stores/vault.ts` | DEK + 解密缓存 |
| `frontend/src/api/vault.ts` | API 客户端 |
| `frontend/src/views/vault/*` | setup/unlock/详情/编辑/模板 |
| `frontend/src/views/VaultHomeView.vue` | 列表接通 |
| `CURSOR.md` | 进度清单 |

---

### Task 1: Flyway 表结构

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__create_vault_tables.sql`

- [ ] **Step 1: 编写幂等建表 SQL**

表：`vault_key_bundle`、`vault_item`、`private_template`（列见设计文档）。索引：`(owner_id, updated_at)`；外键 `owner_id → app_user(id)`。使用 `IF NOT EXISTS` / 条件建索引风格对齐 `V4`。

- [ ] **Step 2: 本地/测试迁移可加载**

Run: `Set-Location D:\Ai\online-word\backend; .\mvnw.cmd "-Djava.version=21" test -Dtest=AuthenticationFlowIntegrationTest`
Expected: 通过（验证 Flyway 不炸）

---

### Task 2: 后端 key-bundle API

**Files:**
- Create: `vault/domain/VaultKeyBundle.java`
- Create: `vault/infrastructure/VaultKeyBundleRepository.java`
- Create: `vault/application/VaultKeyBundleService.java`
- Create: `vault/application/VaultNotInitializedException.java` / `VaultAlreadyInitializedException.java`
- Create: `vault/web/VaultKeyBundleController.java` + Request/Response DTO（byte[] ↔ Base64）

**Interfaces:**
- Produces: `GET/PUT /api/v1/vault/key-bundle`
- Repository: `Optional<VaultKeyBundle> findByOwnerId(String ownerId)`

- [ ] **Step 1: 写集成测试骨架（未初始化 404、创建成功、重复 409、他用户不可见）**
- [ ] **Step 2: 实现实体/仓库/服务/控制器/异常处理**
- [ ] **Step 3: 跑测试通过**

---

### Task 3: 后端 items + private-templates API

**Files:**
- Create: `VaultItem` / `PrivateTemplate` 实体与 Repository
- Create: `VaultItemService` / `PrivateTemplateService`
- Create: `VaultItemController` / `PrivateTemplateController`
- Create: `VaultRevisionConflictException` → 409
- Modify: `GlobalExceptionHandler`

**Interfaces:**
- Items: list/get/create/update/delete（软删）
- Templates: 同上
- 所有写操作校验 `ownerId` + `revision`

- [ ] **Step 1: 扩展集成测试（CRUD、跨用户 404、revision 冲突）**
- [ ] **Step 2: 实现并通过测试**

验收：响应无明文业务字段；仅信封 + 元数据时间戳。

---

### Task 4: 前端 crypto + vault store + setup/unlock

**Files:**
- Modify: `frontend/package.json` — 依赖 `libsodium-wrappers`
- Create: `frontend/src/crypto/sodium.ts` — ready + 随机字节
- Create: `frontend/src/crypto/vaultCrypto.ts` — setupBundle / unlockWithMaster / unlockWithRecovery / encryptPayload / decryptPayload
- Create: `frontend/src/stores/vault.ts`
- Create: `frontend/src/api/vault.ts`
- Create: `frontend/src/views/vault/VaultSetupView.vue`、`VaultUnlockView.vue`
- Modify: `frontend/src/router/index.ts`

**Interfaces（TypeScript）：**

```ts
type KeyBundleDto = {
  kdfSaltBase64: string
  kdfOpsLimit: number
  kdfMemLimit: number
  wrappedDekMasterBase64: string
  wrappedDekMasterNonceBase64: string
  wrappedDekRecoveryBase64: string
  wrappedDekRecoveryNonceBase64: string
  algoVersion: number
  revision: number
}

type CipherEnvelopeDto = {
  id: string
  ciphertextBase64: string
  nonceBase64: string
  algoVersion: number
  payloadVersion: number
  revision: number
  createdAt?: string
  updatedAt?: string
}
```

Argon2id：使用 `sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE` / `MEMLIMIT_INTERACTIVE`（可后续调参）。

- [ ] **Step 1: 安装依赖并实现 crypto 模块**
- [ ] **Step 2: vault store（dek: Uint8Array | null；lock/clear；items 缓存）**
- [ ] **Step 3: 路由守卫：已登录 → 查 key-bundle → 无则 setup，有则未解锁进 unlock**
- [ ] **Step 4: `npm run build` 类型检查通过**

---

### Task 5: 列表 / 详情复制 / 新建编辑动态字段

**Files:**
- Modify: `VaultHomeView.vue` — 解锁后拉 items、客户端解密、筛选搜索
- Create: `VaultItemDetailView.vue` — 默认明文 + 单字段复制
- Create: `VaultItemEditorView.vue` — 动态字段增删改排序（简易上下移即可）
- Create: `frontend/src/domain/vaultPayload.ts` — Zod schema

- [ ] **Step 1: 接通列表元数据（无敏感值列）**
- [ ] **Step 2: 详情复制按钮（`navigator.clipboard.writeText`）**
- [ ] **Step 3: 新建/编辑保存加密上传**

验收：敏感字段默认可见；列表无密码列。

---

### Task 6: 私人模板基础 CRUD + 从模板建记录

**Files:**
- Create: `VaultTemplatesView.vue`
- Modify: 编辑器支持「从模板预填字段结构」
- Modify: `VaultHomeView` 导航链到 `/vault/templates`

- [ ] **Step 1: 模板列表/新建/删除（密文）**
- [ ] **Step 2: `/vault/new?templateId=` 预填 fields 无 value**

---

### Task 7: 锁定/恢复与文档

**Files:**
- Modify: `VaultHomeView` 锁定按钮；`auth.logout` 时 `vault.lock()`
- Unlock 页支持「使用恢复密钥」
- Modify: `CURSOR.md` 已完成/未实现清单

- [ ] **Step 1: 退出与锁定清 DEK**
- [ ] **Step 2: 恢复密钥解锁**
- [ ] **Step 3: 更新 CURSOR.md**
- [ ] **Step 4: 跑后端 vault 集成测试 + `npm run build`**

---

## 切片验收速查

| 切片 | 验收 |
| --- | --- |
| 1 DB | Flyway V8 幂等；测试库迁移成功 |
| 2 key-bundle | GET 404 / PUT 201 / 重复 409 / 所有者隔离 |
| 3 items/templates | CRUD + revision 409 + 跨用户 404 |
| 4 crypto/UI 门禁 | 设置→恢复密钥展示→解锁 |
| 5 记录 UX | 列表元数据、详情明文、单字段复制、动态字段 |
| 6 模板 | CRUD + 从模板新建 |
| 7 锁定 | lock/logout 清内存；恢复密钥可解锁 |

## Spec 覆盖自检

- [x] 方案一密钥模型 → Task 2/4
- [x] 密文 CRUD → Task 3/5
- [x] 动态字段 → Task 5
- [x] 私人模板 → Task 6
- [x] UX 明文/复制 → Task 5
- [x] 非目标已排除 → 不建系统模板/TOTP/备份任务
