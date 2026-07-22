# 保险箱改为服务端加密存储设计

> 状态：已实现（待部署清空旧密文）  
> 日期：2026-07-22  
> 决策来源：用户确认「登录密码与保险箱加解密脱钩；方案 B 服务端加密；旧密文清空重建」  
> 覆盖：`2026-07-21-vault-core-design.md`、`2026-07-21-auth-security-questions-and-login-unlock-design.md` 中与「客户端 DEK / 登录密码包装 / 重置清库」冲突的条款

## 1. 目标与非目标

### 1.1 目标

1. **登录密码只负责认证**：注册、登录、密保重置登录密码；不再参与保险箱加解密。
2. **账密由服务端加密后入库**：应用持有密钥；对已登录用户的 API 仍返回/接收明文业务 JSON。
3. **重置登录密码后保险箱数据保留**：用户用新密码登录即可继续读写原有记录与模板。
4. **去掉客户端密钥体系**：无 DEK、无 key-bundle、无 setup 开箱、无恢复密钥、无 sessionStorage DEK。
5. **旧客户端密文不可迁移**：上线时清空 `vault_key_bundle` / `vault_item` / `private_template`，用户重新录入。

### 1.2 非目标（本轮不做）

- 端到端零知识 / 客户端持有唯一密钥
- 按字段选择性加密（整份 payload JSON 一次加密即可）
- 短信/邮箱重置登录密码
- 管理员查看用户账密明文（管理端仍不提供读 vault 内容的接口）
- 多租户独立加密密钥（首期全局应用密钥；预留 `key_id` 便于日后轮换）

## 2. 威胁模型与信任边界（变更说明）

| 之前（客户端信封） | 之后（服务端加密） |
| --- | --- |
| 服务端/DBA 只见密文，无法解密 | 持有应用密钥的服务端可解密；DB 落盘为密文 |
| 忘记登录密码 ⇒ 无法解包或只能清库 | 忘记登录密码 ⇒ 密保重置后数据仍可用 |
| 用户承担备份恢复密钥责任 | 用户无密钥概念；密钥由运维配置 |

**仍要防的：** 未登录访问、越权读他人记录、API 未鉴权、传输层明文（继续强制 HTTPS）。  
**不再承诺的：** 「服务器被拖库且应用密钥同泄露时账密仍不可读」——应用密钥与 DB 必须分权保管。

## 3. 加密方案

### 3.1 算法

- **算法**：AES-256-GCM（Java `AES/GCM/NoPadding`）
- **密钥**：256-bit，来自配置 `online-safe.vault.encryption-key`（Base64 编码 32 字节），**禁止**提交到仓库；生产用环境变量注入
- **Nonce**：每次加密随机 12 字节
- **AAD**（附加认证数据，防调包）：`ownerId|entityType|entityId|payloadVersion`  
  - `entityType`：`ITEM` 或 `TEMPLATE`
- **密文格式（库内）**：`nonce(12) || ciphertext+tag` 存 `LONGBLOB`；或分列 `nonce` + `ciphertext`（实现任选一种，API 不暴露）
- **`algo_version`**：`2` 表示本方案（与旧客户端 `1` 区分）；读到非 2 的行视为非法/迁移残留并拒绝（迁移后表应为空或已清空）

### 3.2 加解密时机

```text
客户端 ──明文 JSON──► API（已登录）
服务端：校验 schema → AES-GCM 加密 → 写 DB
服务端：读 DB → 解密 → 校验 schema → 明文 JSON ──► 客户端
```

管理员接口**不**增加解密读账密能力。

### 3.3 密钥轮换（预留，本轮可不实现轮换流程）

- 表增加 `key_id`（SMALLINT，默认 `1`）
- 配置可挂多把密钥映射；解密按行 `key_id` 选钥，加密用「当前写密钥」
- 本轮最少：单密钥 + `key_id=1` 列，避免下次改表

## 4. 数据模型（Flyway V10）

### 4.1 迁移动作

1. **TRUNCATE / DELETE** 清空：`vault_item`、`private_template`、`vault_key_bundle`（开发/现网均接受数据丢失）。
2. **删除** `vault_key_bundle` 表（及实体、API、前端调用）。
3. **改造** `vault_item` / `private_template`：

| 列 | 说明 |
| --- | --- |
| `id` | 不变 |
| `owner_id` | 不变 |
| `ciphertext` | 服务端 AES-GCM 密文（含或分列 nonce） |
| `nonce` | 若分列则 BINARY(12)；若合并进 blob 可删列 |
| `algo_version` | 固定写 `2` |
| `payload_version` | 业务 JSON schema 版本，从 `1` 起 |
| `key_id` | 新增，默认 `1` |
| `deleted_at` / 时间戳 / `version` | 保留乐观锁与软删（item）；模板可继续软删或硬删，与现状一致 |

**不在 DB 增加** `name`/`platform` 等明文业务列（首期列表筛选仍在客户端对已解密结果过滤；若后续要服务端搜索，再单独立项加索引列）。

### 4.2 业务 JSON（与前端 `vaultPayload` 对齐）

**VaultItem（payload_version = 1）：**

- `name`, `platform`, `channel`, `status`（`NORMAL`\|`ABNORMAL`\|`EXPIRED`）
- `expiresAt`（`YYYY-MM-DD` 或 null）
- `tags`（数组，可空）
- `fields[]`（含 `systemKey?: account|password`，类型 `TEXT|PASSWORD|EMAIL|URL` 等）
- `templateSnapshot`, `notes`

**PrivateTemplate：**

- `name`, `platform`, `channel`, `fields[]`（无真实密文值或空值）

服务端用与前端等价的校验（Bean Validation 或手动校验）；非法请求 `400`。

## 5. API 变更

### 5.1 删除

| 方法 | 路径 |
| --- | --- |
| GET/PUT/PATCH | `/api/v1/vault/key-bundle` |

### 5.2 记录 / 模板（形态变更）

路径保持：

- `/api/v1/vault/items`、`/api/v1/vault/items/{id}`
- `/api/v1/vault/private-templates`、`/api/v1/vault/private-templates/{id}`

**请求/响应**改为明文业务 DTO + 元数据，例如：

```json
{
  "id": "…",
  "revision": 3,
  "createdAt": "…",
  "updatedAt": "…",
  "payload": { "name": "…", "platform": "…", "fields": [ … ] }
}
```

- 创建：客户端可传 `id`（UUID）或由服务端生成（二选一，实现时与现前端 `createEntityId` 对齐，建议**仍允许客户端传 id** 以减少改动）。
- 更新：带 `revision` 乐观锁；冲突 `409`。
- 列表：返回当前用户全部未删记录的明文 `payload`（注意响应体变大；首期可接受）。
- **禁止**再收发 `ciphertextBase64` / `nonceBase64` / wrapped DEK 字段。

### 5.3 密码重置

`POST /api/auth/password-reset/confirm`：

1. 校验密保 → 更新登录密码哈希  
2. 吊销该用户 session  
3. **删除** `clearVaultData` 调用  

验收：重置后重新登录，原 vault 记录仍在且可解密展示。

## 6. 前端变更

1. **删除/停用**：`crypto/vaultCrypto.ts` 中 setup/open/encrypt/decrypt 路径、`dekSession.ts`、libsodium 依赖（若无其它用途则移除）、`VaultSetupView` 与路由门禁 `vaultGate: 'setup'`。
2. **`stores/vault.ts`**：登录后直接 `loadItems`/`loadTemplates`；`save*` 提交明文 `payload`；去掉 `dek` / `dekReady` / `keyBundle` / `ephemeralLoginPassword` 解包逻辑。
3. **`stores/auth.ts`**：登录成功不再持有密码用于开箱；登出无需 `clearDek`。
4. **`api/vault.ts`**：DTO 改为明文 payload；删除 key-bundle API。
5. **文案**：找回密码页去掉「需重新初始化保险箱 / 旧密文无法保留」；顶栏去掉「已登录·详情默认明文」中暗示客户端加密的表述（可改为「已登录」等）。
6. **路由**：`/vault/setup` 重定向到 `/vault`；无初始化拦截。

## 7. 配置与运维

```yaml
# application 示例（密钥勿入库真实值）
online-safe:
  vault:
    encryption-key: ${VAULT_ENCRYPTION_KEY}   # Base64(32 bytes)
    current-key-id: 1
```

- 启动时若密钥缺失或长度非法 → **fail-fast** 拒绝启动。
- 文档补充：密钥备份、与数据库凭证分离存放。

## 8. 测试

1. 集成测试：创建 item → DB 中 `ciphertext` 非明文 JSON 子串；GET 返回明文。
2. 越权：用户 A 不能读 B 的 item。
3. 乐观锁 revision 冲突。
4. 密码重置后 vault 数据仍在。
5. 缺密钥启动失败（单测或上下文测试）。
6. 删除旧 `VaultFlowIntegrationTest` 中 key-bundle / 客户端信封断言，改为新契约。

## 9. 发布步骤

1. 合并代码 + Flyway V10（清表 + 改表 + 删 key_bundle）。  
2. 配置生产 `VAULT_ENCRYPTION_KEY`。  
3. 部署后端 → 前端。  
4. 通知用户：旧保险箱密文已清空，请重新录入；重置密码不再丢数据。

## 10. 验收清单

- [ ] 登录后无需 setup/解锁即可 CRUD 账密与模板  
- [ ] DB 中看不到明文密码字段值（抽检 ciphertext）  
- [ ] 密保重置登录密码后，原记录仍可打开  
- [ ] key-bundle API 404 或已移除  
- [ ] 前端无 libsodium 开箱路径、无「重建保险箱」误导文案  

## 11. 实现分期建议

| 阶段 | 内容 |
| --- | --- |
| P0 | Flyway V10、服务端加解密组件、Item/Template API 明文 DTO、删 key-bundle、重置不清库 |
| P1 | 前端去 crypto/setup、改 store/api/文案 |
| P2 | 测试与文档（更新旧 vault-core / login-unlock 规格状态为「已废止相关条款」） |
