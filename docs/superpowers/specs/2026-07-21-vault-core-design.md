# 保险箱核心（密钥信封 + 密文 CRUD）设计

> 状态：已确认，进入实现
> 日期：2026-07-21
> 范围：首期 A + B + C（密钥信封、密文 CRUD/首页、动态字段、私人模板）

## 1. 目标

打通个人用户从「登录网站 → 设置/解锁保险箱 → 密文记录增删改查 → 动态字段编辑 → 私人模板」的闭环，且服务端与管理员全程只接触密文。

对齐：

- `在线账密保险箱-产品需求文档-v1.0.md`
- `在线账密保险箱-技术选型与架构设计-v1.0.md` 第 7–8 节
- `CURSOR.md` / `AGENTS.md` / `docs/Design.md`（UX 处以下文用户决策为准）

## 2. 加密路线：方案一（客户端全权）

| 角色 | 职责 |
| --- | --- |
| 浏览器（libsodium.js） | Argon2id 派生、DEK 生成与包装、记录/模板加解密 |
| 服务端 | 纯密文仓库：校验信封结构、所有者隔离、乐观锁；**不解密、不派生密钥** |
| Pinia | 内存保存 DEK 与解密缓存；标签页 `sessionStorage` 暂存 DEK 供刷新恢复；禁止 localStorage / 持久化插件 |

### 2.1 密钥模型

```text
登录密码 ──Argon2id(salt, ops, mem)──► KEK_login ──XChaCha20-Poly1305──► wrapped_dek_master（列名保留，语义为登录密码包装）
随机 DEK（32 字节）──XChaCha20-Poly1305(+AAD)──► 每条 vault_item / private_template 密文
```

约定：

1. 首次初始化保险箱：客户端生成 DEK；用登录密码派生密钥包装；上传 `vault_key_bundle`（`wrapped_dek_recovery*` 仅兼容写库，密钥立即丢弃，不向用户展示）。
2. 登录成功：下载 key-bundle → 登录密码派生 KEK → 解包 DEK → 存入 Pinia 与标签页 `sessionStorage`（用户无感知「解锁」步骤）。
3. 密保重置登录密码：服务端清除该用户密钥信封与全部密文；下次登录走 setup 重建空保险箱。**无恢复密钥重包装**。
4. 密保答案只存哈希，不参与 DEK。
5. 记录加密 AAD：`ownerId|itemId|payloadVersion`（UTF-8）。
6. 模板加密 AAD：`ownerId|templateId|payloadVersion`。

详见：`2026-07-21-auth-security-questions-and-login-unlock-design.md`。

### 2.2 客户端明文载荷（加密前 JSON）

**账密记录 `VaultItemPayload`（payloadVersion = 1）：**

```json
{
  "name": "工作邮箱",
  "platform": "Google",
  "channel": "自行注册",
  "status": "NORMAL",
  "tags": ["工作"],
  "fields": [
    {
      "id": "f1",
      "name": "邮箱",
      "type": "EMAIL",
      "value": "a@example.com",
      "required": true,
      "sensitive": false,
      "copyable": true,
      "hint": "",
      "order": 0
    }
  ],
  "templateSnapshot": null,
  "notes": ""
}
```

字段类型（首期）：`TEXT` | `PASSWORD` | `EMAIL` | `PHONE` | `URL` | `CODE` | `DATETIME` | `MULTILINE` | `SELECT`
本轮不做 `TOTP` UI（类型可预留但不渲染生成器）。

**私人模板 `PrivateTemplatePayload`（payloadVersion = 1）：**

- 仅字段名称与配置（含类型、必填、敏感、可复制、提示、顺序、SELECT 选项），**不含字段值**。
- 可含默认 `platform` / `channel` 元数据（明文侧也在密文内）。

### 2.3 服务端信封（API / DB）

服务端只存/传：

- Base64 密文、Base64 nonce（24 字节）
- `algoVersion`（当前 `1` = XChaCha20-Poly1305）
- `payloadVersion`
- `revision`（乐观锁，对应 JPA `@Version`）
- 所有者、时间戳、软删除标记（记录）

**禁止**出现：记录名称、标签、字段值、登录密码、DEK 明文列。

## 3. 数据模型（Flyway）

### 3.1 `vault_key_bundle`

| 列 | 说明 |
| --- | --- |
| `owner_id` PK/UK | 每用户一条 |
| `kdf_salt` | BINARY(16+) |
| `kdf_ops_limit` / `kdf_mem_limit` | Argon2id 参数 |
| `wrapped_dek_master` / `wrapped_dek_master_nonce` | 登录密码包装（列名历史遗留） |
| `wrapped_dek_recovery` / `wrapped_dek_recovery_nonce` | 兼容列；产品不再使用恢复密钥 |
| `algo_version` | 整数 |
| `created_at` / `updated_at` / `version` | 标准审计与乐观锁 |

### 3.2 `vault_item`

| 列 | 说明 |
| --- | --- |
| `id` | UUID |
| `owner_id` | 所有者 |
| `ciphertext` LONGBLOB | 密文 |
| `nonce` BINARY(24) | |
| `algo_version` / `payload_version` | |
| `deleted_at` | 软删除（本轮 API 可硬逻辑删除或软删二选一：采用软删以便后续回收站） |
| `created_at` / `updated_at` / `version` | `version` 即 revision |

查询一律 `findByIdAndOwnerId` / `findByOwnerIdAndDeletedAtIsNull`。

### 3.3 `private_template`

同 `vault_item` 的密文列结构 + `owner_id` 隔离；无软删除亦可（本轮物理删除或软删均可，采用软删 `deleted_at` 与记录一致）。

## 4. API（均需 `ROLE_USER`，路径 `/api/v1/**`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/vault/key-bundle` | 无则 404 `VAULT_NOT_INITIALIZED` |
| PUT | `/api/v1/vault/key-bundle` | 首次创建；已存在则 409 |
| PATCH | `/api/v1/vault/key-bundle` | 保留兼容（产品前端不再调用） |
| GET | `/api/v1/vault/items` | 当前用户未删除密文列表 |
| GET | `/api/v1/vault/items/{id}` | 单条 |
| POST | `/api/v1/vault/items` | 客户端先生成 id，请求带 id + 信封 |
| PUT | `/api/v1/vault/items/{id}` | 带 `revision`；冲突 409 |
| DELETE | `/api/v1/vault/items/{id}` | 软删除 |
| GET/POST | `/api/v1/vault/private-templates` | 列表 / 创建 |
| GET/PUT/DELETE | `/api/v1/vault/private-templates/{id}` | 读改删 |

请求体示例（记录）：

```json
{
  "id": "uuid",
  "ciphertextBase64": "...",
  "nonceBase64": "...",
  "algoVersion": 1,
  "payloadVersion": 1,
  "revision": 0
}
```

响应回显同样信封字段 + `createdAt` / `updatedAt` / `revision`。
Controller → Service → Repository；`ownerId` 仅来自 `AppUserPrincipal.userId()`。

## 5. 前端 UX（用户决策）

| 决策 | 行为 |
| --- | --- |
| 可见性 | 登录并打开信封后可在**详情**完整查看账密字段值 |
| 敏感字段 | **默认明文显示**（不默认打码）；可选「隐藏」为增强，非默认 |
| 复制 | **单字段一键复制** |
| 列表 | 首页展示解密后的名称/平台/渠道/状态/标签/更新时间；**列表不展示密码等敏感字段值** |
| 锁定 | **已删除**：无锁定按钮、无自动锁定 |
| 路由 | `/vault`；`/vault/setup`（初始化）；`/vault/rewrap` 与 `/vault/unlock` 重定向到 `/vault` |

视觉：复用现有 `VaultHomeView` / Vuetify / `Design.md`；勿另起视觉体系。

## 6. 分层与安全边界

- 后端包：`com.godlei.onlinesafe.vault`（`web` / `application` / `domain` / `infrastructure`）。
- 日志：禁止记录密文字段内容以外的「解密内容」；请求体密文可存库但不得打到 info 日志。
- 管理员无 vault 密文业务接口；管理端存储统计本轮仍可为占位。
- 集成测试：用户 A 无法读写用户 B 的 key-bundle / items / templates；revision 冲突返回 409。

## 7. 非目标（本轮明确不做）

- 系统模板全量与管理端模板后台
- TOTP 生成 UI
- 备份导出 / 恢复文件
- 回收站 UI（表可预留软删）
- 粘贴识别录入
- 管理端密文存储量/记录数真值
- Worker 隔离加密线程
- 自动锁定超时策略与锁定按钮
- 服务端搜索索引、跨用户分享
- 短信 / 邮箱重置登录密码

## 8. 验收要点

1. 新用户可初始化保险箱并进入首页；登录成功后即可查看（无解锁页、无恢复密钥展示）。
2. 可新建带动态字段的记录；列表与详情解密正确；单字段复制可用。
3. 敏感字段详情默认明文。
4. 私人模板可 CRUD，并可从模板预填新建表单（无值）。
5. 数据库与 API 响应中无账密明文；跨用户访问 404。
6. 退出登录后清除内存与 `sessionStorage` 中的 DEK；同一标签页刷新可自 `sessionStorage` 恢复，无需再输登录密码。
