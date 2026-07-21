import { requestJson } from '@/api/client'

export type KeyBundleDto = {
  kdfSaltBase64: string
  kdfOpsLimit: number
  kdfMemLimit: number
  wrappedDekMasterBase64: string
  wrappedDekMasterNonceBase64: string
  wrappedDekRecoveryBase64: string
  wrappedDekRecoveryNonceBase64: string
  algoVersion: number
  revision?: number
  createdAt?: string
  updatedAt?: string
}

export type CipherEnvelopeDto = {
  id: string
  ciphertextBase64: string
  nonceBase64: string
  algoVersion: number
  payloadVersion: number
  revision: number
  createdAt?: string
  updatedAt?: string
}

export const vaultApi = {
  getKeyBundle() {
    return requestJson<KeyBundleDto>('/api/v1/vault/key-bundle')
  },
  createKeyBundle(body: Omit<KeyBundleDto, 'revision' | 'createdAt' | 'updatedAt'>) {
    return requestJson<KeyBundleDto>('/api/v1/vault/key-bundle', {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
  listItems() {
    return requestJson<CipherEnvelopeDto[]>('/api/v1/vault/items')
  },
  getItem(id: string) {
    return requestJson<CipherEnvelopeDto>(`/api/v1/vault/items/${id}`)
  },
  createItem(body: CipherEnvelopeDto) {
    return requestJson<CipherEnvelopeDto>('/api/v1/vault/items', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
  updateItem(id: string, body: CipherEnvelopeDto) {
    return requestJson<CipherEnvelopeDto>(`/api/v1/vault/items/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
  deleteItem(id: string) {
    return requestJson<void>(`/api/v1/vault/items/${id}`, { method: 'DELETE' })
  },
  listTemplates() {
    return requestJson<CipherEnvelopeDto[]>('/api/v1/vault/private-templates')
  },
  getTemplate(id: string) {
    return requestJson<CipherEnvelopeDto>(`/api/v1/vault/private-templates/${id}`)
  },
  createTemplate(body: CipherEnvelopeDto) {
    return requestJson<CipherEnvelopeDto>('/api/v1/vault/private-templates', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
  updateTemplate(id: string, body: CipherEnvelopeDto) {
    return requestJson<CipherEnvelopeDto>(`/api/v1/vault/private-templates/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
  deleteTemplate(id: string) {
    return requestJson<void>(`/api/v1/vault/private-templates/${id}`, { method: 'DELETE' })
  },
}
