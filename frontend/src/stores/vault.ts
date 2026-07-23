import { defineStore } from 'pinia'
import { userSystemTemplatesApi, type UserSystemTemplate } from '@/api/systemTemplates'
import { vaultApi, type VaultItemRecord, type VaultTemplateRecord } from '@/api/vault'
import { createEntityId } from '@/domain/entityId'
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
  envelope: Pick<VaultItemRecord, 'id' | 'revision' | 'createdAt' | 'updatedAt'>
  payload: VaultItemPayload
}

export type DecryptedTemplate = {
  envelope: Pick<VaultTemplateRecord, 'id' | 'revision' | 'createdAt' | 'updatedAt'>
  payload: PrivateTemplatePayload
}

function toItem(record: VaultItemRecord): DecryptedItem {
  return {
    envelope: {
      id: record.id,
      revision: record.revision,
      createdAt: record.createdAt,
      updatedAt: record.updatedAt,
    },
    payload: vaultItemPayloadSchema.parse(record.payload),
  }
}

function toTemplate(record: VaultTemplateRecord): DecryptedTemplate {
  return {
    envelope: {
      id: record.id,
      revision: record.revision,
      createdAt: record.createdAt,
      updatedAt: record.updatedAt,
    },
    payload: privateTemplatePayloadSchema.parse(record.payload),
  }
}

export const useVaultStore = defineStore('vault', {
  state: () => ({
    ready: false,
    items: [] as DecryptedItem[],
    templates: [] as DecryptedTemplate[],
    systemTemplates: [] as UserSystemTemplate[],
    lastError: '',
  }),
  getters: {
    /** 兼容旧布局：登录即可用，无需客户端开箱 */
    dekReady: (state) => state.ready,
    initialized: (state) => state.ready,
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
    clearSessionData() {
      this.items = []
      this.templates = []
      this.systemTemplates = []
      this.lastError = ''
      this.ready = false
    },
    async refreshInitialization() {
      try {
        this.requireOwnerId()
        await this.loadItems()
      } catch (error) {
        this.lastError = error instanceof Error ? error.message : '加载保险箱失败'
        throw error
      } finally {
        this.ready = true
      }
    },
    async loadItems() {
      this.requireOwnerId()
      const records = await vaultApi.listItems()
      this.items = records.map(toItem)
    },
    async loadTemplates() {
      this.requireOwnerId()
      const records = await vaultApi.listTemplates()
      this.templates = records.map(toTemplate)
    },
    async loadSystemTemplates() {
      this.requireOwnerId()
      this.systemTemplates = await userSystemTemplatesApi.list()
    },
    async getItem(id: string) {
      const cached = this.items.find((item) => item.envelope.id === id)
      if (cached) return cached
      this.requireOwnerId()
      return toItem(await vaultApi.getItem(id))
    },
    async saveItem(id: string | null, payload: VaultItemPayload) {
      this.requireOwnerId()
      const parsed = vaultItemPayloadSchema.parse(payload)
      const entityId = id ?? createEntityId()
      const existing = id ? this.items.find((item) => item.envelope.id === id) : undefined
      const body = {
        id: entityId,
        payload: parsed,
        revision: existing?.envelope.revision ?? 0,
      }
      const record = existing
        ? await vaultApi.updateItem(entityId, body)
        : await vaultApi.createItem(body)
      await this.loadItems()
      return record.id
    },
    async deleteItem(id: string) {
      await vaultApi.deleteItem(id)
      this.items = this.items.filter((item) => item.envelope.id !== id)
    },
    async saveTemplate(id: string | null, payload: PrivateTemplatePayload) {
      this.requireOwnerId()
      const parsed = privateTemplatePayloadSchema.parse(payload)
      const entityId = id ?? createEntityId()
      const existing = id ? this.templates.find((item) => item.envelope.id === id) : undefined
      const body = {
        id: entityId,
        payload: parsed,
        revision: existing?.envelope.revision ?? 0,
      }
      const record = existing
        ? await vaultApi.updateTemplate(entityId, body)
        : await vaultApi.createTemplate(body)
      await this.loadTemplates()
      return record.id
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
        channelUrl: template.payload.channelUrl || base.channelUrl,
        fields: fieldsFromTemplate(template.payload),
        templateSnapshot: {
          source: 'PRIVATE',
          templateId: template.envelope.id,
          name: template.payload.name,
          fields: template.payload.fields,
        },
      } satisfies VaultItemPayload
    },
    async buildPayloadFromSystemTemplate(templateId: string) {
      let template = this.systemTemplates.find((item) => item.id === templateId)
      if (!template) {
        template = await userSystemTemplatesApi.get(templateId)
      }
      const asPrivate: PrivateTemplatePayload = {
        name: template.name,
        platform: template.platform,
        channel: template.channel,
        channelUrl: template.channelUrl,
        fields: template.fields.map((field) => ({
          id: field.id,
          name: field.name,
          type: field.type as PrivateTemplatePayload['fields'][number]['type'],
          required: field.required,
          sensitive: field.sensitive,
          copyable: field.copyable,
          hint: field.hint,
          order: field.order,
          systemKey: field.systemKey,
          value: '',
        })),
      }
      const base = emptyItemPayload()
      return {
        ...base,
        name: '',
        platform: asPrivate.platform || base.platform,
        channel: asPrivate.channel || base.channel,
        channelUrl: asPrivate.channelUrl || base.channelUrl,
        fields: fieldsFromTemplate(asPrivate),
        templateSnapshot: {
          source: 'SYSTEM',
          templateId: template.id,
          name: template.name,
          fields: asPrivate.fields,
        },
      } satisfies VaultItemPayload
    },
  },
})
