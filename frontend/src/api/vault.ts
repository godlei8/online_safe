import { requestJson } from '@/api/client'
import type { PrivateTemplatePayload, VaultItemPayload } from '@/domain/vaultPayload'

export type VaultRecordDto<T> = {
  id: string
  payload: T
  revision: number
  createdAt?: string
  updatedAt?: string
}

export type VaultItemRecord = VaultRecordDto<VaultItemPayload>
export type VaultTemplateRecord = VaultRecordDto<PrivateTemplatePayload>

export const vaultApi = {
  listItems() {
    return requestJson<VaultItemRecord[]>('/api/v1/vault/items')
  },
  getItem(id: string) {
    return requestJson<VaultItemRecord>(`/api/v1/vault/items/${id}`)
  },
  createItem(body: { id: string; payload: VaultItemPayload; revision: number }) {
    return requestJson<VaultItemRecord>('/api/v1/vault/items', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
  updateItem(id: string, body: { id: string; payload: VaultItemPayload; revision: number }) {
    return requestJson<VaultItemRecord>(`/api/v1/vault/items/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
  deleteItem(id: string) {
    return requestJson<void>(`/api/v1/vault/items/${id}`, { method: 'DELETE' })
  },
  listTemplates() {
    return requestJson<VaultTemplateRecord[]>('/api/v1/vault/private-templates')
  },
  getTemplate(id: string) {
    return requestJson<VaultTemplateRecord>(`/api/v1/vault/private-templates/${id}`)
  },
  createTemplate(body: { id: string; payload: PrivateTemplatePayload; revision: number }) {
    return requestJson<VaultTemplateRecord>('/api/v1/vault/private-templates', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
  updateTemplate(id: string, body: { id: string; payload: PrivateTemplatePayload; revision: number }) {
    return requestJson<VaultTemplateRecord>(`/api/v1/vault/private-templates/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
  deleteTemplate(id: string) {
    return requestJson<void>(`/api/v1/vault/private-templates/${id}`, { method: 'DELETE' })
  },
}
