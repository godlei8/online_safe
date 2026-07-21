import { defineStore } from 'pinia'
import { ApiRequestError } from '@/api/client'
import { vaultApi, type CipherEnvelopeDto, type KeyBundleDto } from '@/api/vault'
import {
  createEntityId,
  decryptJson,
  encryptJson,
  openWithLoginPassword,
  setupVault,
} from '@/crypto/vaultCrypto'
import {
  emptyItemPayload,
  fieldsFromTemplate,
  privateTemplatePayloadSchema,
  statusLabel,
  vaultItemPayloadSchema,
  type PrivateTemplatePayload,
  type VaultItemPayload,
} from '@/domain/vaultPayload'
import { useAuthStore } from '@/stores/auth'

export type DecryptedItem = {
  envelope: CipherEnvelopeDto
  payload: VaultItemPayload
}

export type DecryptedTemplate = {
  envelope: CipherEnvelopeDto
  payload: PrivateTemplatePayload
}

export const useVaultStore = defineStore('vault', {
  state: () => ({
    ready: false,
    initialized: false as boolean | null,
    /** 内存中是否已有 DEK（非「解锁」产品概念） */
    dekReady: false,
    /** 会话仍在但刷新后 DEK 丢失，需确认登录密码 */
    needsLoginPassword: false,
    dek: null as Uint8Array | null,
    keyBundle: null as KeyBundleDto | null,
    items: [] as DecryptedItem[],
    templates: [] as DecryptedTemplate[],
    lastError: '',
  }),
  getters: {
    listRows(state) {
      return state.items.map(({ envelope, payload }) => ({
        id: envelope.id,
        name: payload.name,
        platform: payload.platform,
        channel: payload.channel || '未填写',
        status: statusLabel[payload.status],
        statusKey: payload.status,
        tags: payload.tags,
        updatedAt: envelope.updatedAt ?? '',
        updatedAtMs: envelope.updatedAt ? Date.parse(envelope.updatedAt) : 0,
      }))
    },
  },
  actions: {
    requireOwnerId() {
      const auth = useAuthStore()
      const ownerId = auth.session.userId
      if (!ownerId) throw new Error('未登录')
      return ownerId
    },
    requireDek() {
      if (!this.dek) throw new Error('请先确认登录密码以继续查看')
      return this.dek
    },
    clearDek() {
      this.dek = null
      this.dekReady = false
      this.items = []
      this.templates = []
      this.lastError = ''
    },
    async refreshInitialization() {
      try {
        this.keyBundle = await vaultApi.getKeyBundle()
        this.initialized = true
        if (!this.dek) {
          this.needsLoginPassword = true
        }
      } catch (error) {
        if (error instanceof ApiRequestError && error.status === 404) {
          this.keyBundle = null
          this.initialized = false
          this.clearDek()
          this.needsLoginPassword = false
          return
        }
        throw error
      } finally {
        this.ready = true
      }
    },
    async setup(loginPassword: string) {
      const result = await setupVault(loginPassword)
      this.keyBundle = await vaultApi.createKeyBundle(result.bundle)
      this.dek = result.dek
      this.initialized = true
      this.dekReady = true
      this.needsLoginPassword = false
      await this.loadItems()
    },
    async openWithPassword(loginPassword: string) {
      if (!this.keyBundle) await this.refreshInitialization()
      if (!this.keyBundle) throw new Error('尚未初始化保险箱')
      this.dek = await openWithLoginPassword(this.keyBundle, loginPassword)
      this.dekReady = true
      this.needsLoginPassword = false
      await this.loadItems()
    },
    async loadItems() {
      const ownerId = this.requireOwnerId()
      const dek = this.requireDek()
      const envelopes = await vaultApi.listItems()
      const items: DecryptedItem[] = []
      for (const envelope of envelopes) {
        const payload = vaultItemPayloadSchema.parse(
          await decryptJson<VaultItemPayload>(envelope, dek, ownerId, envelope.id),
        )
        items.push({ envelope, payload })
      }
      this.items = items
    },
    async loadTemplates() {
      const ownerId = this.requireOwnerId()
      const dek = this.requireDek()
      const envelopes = await vaultApi.listTemplates()
      const templates: DecryptedTemplate[] = []
      for (const envelope of envelopes) {
        const payload = privateTemplatePayloadSchema.parse(
          await decryptJson<PrivateTemplatePayload>(envelope, dek, ownerId, envelope.id),
        )
        templates.push({ envelope, payload })
      }
      this.templates = templates
    },
    async getItem(id: string) {
      const cached = this.items.find((item) => item.envelope.id === id)
      if (cached) return cached
      const ownerId = this.requireOwnerId()
      const dek = this.requireDek()
      const envelope = await vaultApi.getItem(id)
      const payload = vaultItemPayloadSchema.parse(
        await decryptJson<VaultItemPayload>(envelope, dek, ownerId, envelope.id),
      )
      return { envelope, payload }
    },
    async saveItem(id: string | null, payload: VaultItemPayload) {
      const ownerId = this.requireOwnerId()
      const dek = this.requireDek()
      const parsed = vaultItemPayloadSchema.parse(payload)
      const entityId = id ?? createEntityId()
      const encrypted = await encryptJson(parsed, dek, ownerId, entityId)
      const existing = id ? this.items.find((item) => item.envelope.id === id) : undefined
      const body = {
        id: entityId,
        ...encrypted,
        revision: existing?.envelope.revision ?? 0,
      }
      const envelope = existing
        ? await vaultApi.updateItem(entityId, body)
        : await vaultApi.createItem(body)
      await this.loadItems()
      return envelope.id
    },
    async deleteItem(id: string) {
      await vaultApi.deleteItem(id)
      this.items = this.items.filter((item) => item.envelope.id !== id)
    },
    async saveTemplate(id: string | null, payload: PrivateTemplatePayload) {
      const ownerId = this.requireOwnerId()
      const dek = this.requireDek()
      const parsed = privateTemplatePayloadSchema.parse(payload)
      const entityId = id ?? createEntityId()
      const encrypted = await encryptJson(parsed, dek, ownerId, entityId)
      const existing = id ? this.templates.find((item) => item.envelope.id === id) : undefined
      const body = {
        id: entityId,
        ...encrypted,
        revision: existing?.envelope.revision ?? 0,
      }
      const envelope = existing
        ? await vaultApi.updateTemplate(entityId, body)
        : await vaultApi.createTemplate(body)
      await this.loadTemplates()
      return envelope.id
    },
    async deleteTemplate(id: string) {
      await vaultApi.deleteTemplate(id)
      this.templates = this.templates.filter((item) => item.envelope.id !== id)
    },
    async buildPayloadFromTemplate(templateId: string) {
      await this.loadTemplates()
      const template = this.templates.find((item) => item.envelope.id === templateId)
      if (!template) throw new Error('模板不存在')
      const base = emptyItemPayload()
      return {
        ...base,
        platform: template.payload.platform || base.platform,
        channel: template.payload.channel || base.channel,
        fields: fieldsFromTemplate(template.payload),
        templateSnapshot: {
          templateId: template.envelope.id,
          name: template.payload.name,
          fields: template.payload.fields,
        },
      } satisfies VaultItemPayload
    },
  },
})
