# 服务端加密保险箱 Implementation Plan

> **For agentic workers:** 按任务顺序实现；每任务可独立验证。

**Goal:** 登录密码与保险箱加解密脱钩；账密服务端 AES-GCM 加密入库；重置密码不清库；清空旧客户端密文。

**Architecture:** API 收发明文 `payload`；服务端用应用密钥加密后写入 `vault_item`/`private_template`；删除 `vault_key_bundle` 与前端 DEK/setup。

**Tech Stack:** Spring Boot + AES-256-GCM、Flyway V10、Vue3/Pinia

## Global Constraints

- 中文优先（AGENTS.md）
- 密钥：`app.vault.encryption-key`（Base64 32 字节），缺钥 fail-fast
- `algo_version=2`；旧密文清空不可迁移
- 重置密码：只改密码 + 吊销会话，不清 vault

---

## Task 1：后端加密组件 + 配置

- [ ] `VaultPayloadCipher`（AES-GCM + AAD）
- [ ] `application.yml` / test / prod / local.example 增加 `app.vault.encryption-key`
- [ ] Flyway `V10__server_side_vault_encryption.sql`：清空三表、删 key_bundle、nonce→12、加 `key_id`

## Task 2：实体与 Item/Template API 明文 DTO

- [ ] 改造 `VaultItem` / `PrivateTemplate`（nonce 12、`keyId`）
- [ ] `VaultRecordRequest` / `VaultRecordResponse`（`id` + `payload` + `revision`）
- [ ] 重写 `VaultItemService` / `PrivateTemplateService`：加解密出入
- [ ] 更新 Controllers；删除 key-bundle 全链路

## Task 3：密码重置 + 测试

- [ ] `PasswordResetService` 去掉 `clearVaultData`
- [ ] 重写 `VaultFlowIntegrationTest`、调整 `SecurityQuestionPasswordResetIntegrationTest`
- [ ] 清理 GlobalExceptionHandler 中已删异常（可选保留无害）

## Task 4：前端去客户端加密

- [ ] 重写 `api/vault.ts`、`stores/vault.ts`、`stores/auth.ts`、`api/client.ts`
- [ ] 路由去掉 setup 门禁；`VaultLayout` 不再依赖 `dekReady`
- [ ] 找回密码文案；删除/停用 crypto 与 VaultSetupView 引用
- [ ] `createEntityId` 迁到无 sodium 的 util

## Task 5：验证

- [ ] `mvn test`（vault + password reset）
- [ ] 前端 `npm run build` 或 typecheck
