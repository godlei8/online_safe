import { requestJson } from './client'
import type { FieldType } from '@/domain/vaultPayload'

export type SystemTemplateStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export type SystemTemplateField = {
  id: string
  name: string
  type: FieldType | string
  value?: string
  required: boolean
  sensitive: boolean
  copyable: boolean
  hint: string
  order: number
  systemKey?: 'account' | 'password'
}

export type AdminSystemTemplate = {
  id: string
  name: string
  platform: string
  channel: string
  channelUrl: string
  fields: SystemTemplateField[]
  status: SystemTemplateStatus
  sortOrder: number
  createdByAdminId: string
  updatedByAdminId: string
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  revision: number
}

export type SystemTemplatePage = {
  content: AdminSystemTemplate[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type SystemTemplateUpsertPayload = {
  name: string
  platform: string
  channel?: string
  channelUrl?: string
  fields: SystemTemplateField[]
  sortOrder?: number
}

export type UserSystemTemplate = {
  id: string
  name: string
  platform: string
  channel: string
  channelUrl: string
  fields: SystemTemplateField[]
  sortOrder: number
  publishedAt: string | null
  updatedAt: string
}

export type AdminSystemTemplateQuery = {
  page?: number
  size?: number
  status?: SystemTemplateStatus | ''
  platform?: string
  q?: string
}

function toAdminQuery(params: AdminSystemTemplateQuery) {
  const search = new URLSearchParams()
  if (params.page != null) search.set('page', String(params.page))
  if (params.size != null) search.set('size', String(params.size))
  if (params.status) search.set('status', params.status)
  if (params.platform?.trim()) search.set('platform', params.platform.trim())
  if (params.q?.trim()) search.set('q', params.q.trim())
  const text = search.toString()
  return text ? `?${text}` : ''
}

function toUserQuery(params: { platform?: string; q?: string }) {
  const search = new URLSearchParams()
  if (params.platform?.trim()) search.set('platform', params.platform.trim())
  if (params.q?.trim()) search.set('q', params.q.trim())
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const adminSystemTemplatesApi = {
  list(params: AdminSystemTemplateQuery = {}) {
    return requestJson<SystemTemplatePage>(`/api/admin/v1/system-templates${toAdminQuery(params)}`)
  },
  get(id: string) {
    return requestJson<AdminSystemTemplate>(`/api/admin/v1/system-templates/${id}`)
  },
  create(body: SystemTemplateUpsertPayload) {
    return requestJson<AdminSystemTemplate>('/api/admin/v1/system-templates', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
  update(id: string, body: SystemTemplateUpsertPayload) {
    return requestJson<AdminSystemTemplate>(`/api/admin/v1/system-templates/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
  publish(id: string) {
    return requestJson<AdminSystemTemplate>(`/api/admin/v1/system-templates/${id}/publish`, {
      method: 'POST',
    })
  },
  offline(id: string) {
    return requestJson<AdminSystemTemplate>(`/api/admin/v1/system-templates/${id}/offline`, {
      method: 'POST',
    })
  },
  updateSort(id: string, sortOrder: number) {
    return requestJson<AdminSystemTemplate>(`/api/admin/v1/system-templates/${id}/sort`, {
      method: 'PATCH',
      body: JSON.stringify({ sortOrder }),
    })
  },
  remove(id: string) {
    return requestJson<void>(`/api/admin/v1/system-templates/${id}`, { method: 'DELETE' })
  },
}

export const userSystemTemplatesApi = {
  list(params: { platform?: string; q?: string } = {}) {
    return requestJson<UserSystemTemplate[]>(`/api/v1/system-templates${toUserQuery(params)}`)
  },
  get(id: string) {
    return requestJson<UserSystemTemplate>(`/api/v1/system-templates/${id}`)
  },
}
