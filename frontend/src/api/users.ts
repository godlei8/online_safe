import { requestJson } from './client'

export type ManagedUser = {
  id: string
  username: string
  maskedPhone: string
  status: 'ACTIVE' | 'DISABLED'
  createdAt: string
  lastLoginAt: string | null
  activeSessionCount: number
  cipherStorageBytes: number | null
  recordCount: number | null
}

export type ManagedUserStats = {
  total: number
  active: number
  disabled: number
  activeLast7Days: number
}

export type ManagedUserPage = {
  content: ManagedUser[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type ManagedUserQuery = {
  page?: number
  size?: number
  q?: string
  status?: string
  registeredWithin?: string
}

function toQuery(params: ManagedUserQuery) {
  const search = new URLSearchParams()
  if (params.page != null) search.set('page', String(params.page))
  if (params.size != null) search.set('size', String(params.size))
  if (params.q) search.set('q', params.q)
  if (params.status) search.set('status', params.status)
  if (params.registeredWithin) search.set('registeredWithin', params.registeredWithin)
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const usersApi = {
  list: (params: ManagedUserQuery = {}) =>
    requestJson<ManagedUserPage>(`/api/admin/v1/users${toQuery(params)}`),
  stats: () => requestJson<ManagedUserStats>('/api/admin/v1/users/stats'),
  disable: (id: string) =>
    requestJson<ManagedUser>(`/api/admin/v1/users/${id}/disable`, { method: 'POST' }),
  enable: (id: string) =>
    requestJson<ManagedUser>(`/api/admin/v1/users/${id}/enable`, { method: 'POST' }),
  revokeSessions: (id: string) =>
    requestJson<ManagedUser>(`/api/admin/v1/users/${id}/revoke-sessions`, { method: 'POST' }),
}
