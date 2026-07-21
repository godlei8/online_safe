import { requestJson } from './client'

export type Invitation = {
  id: string
  codeHint: string
  type: 'SINGLE' | 'MULTI'
  usedCount: number
  maxUses: number
  expiresAt: string | null
  status: string
  creatorUsername: string
  lastUsedAt: string | null
  note: string | null
  createdAt: string
}

export type InvitationStats = {
  total: number
  active: number
  expiringSoon: number
  disabledOrExhausted: number
}

export type InvitationPage = {
  content: Invitation[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type CreateInvitationPayload = {
  maxUses: number
  expiresAt?: string | null
  note?: string | null
}

export type CreateInvitationResponse = {
  invitation: Invitation
  plainCode: string
}

export type InvitationQuery = {
  page?: number
  size?: number
  status?: string
  type?: string
  q?: string
}

function toQuery(params: InvitationQuery) {
  const search = new URLSearchParams()
  if (params.page != null) search.set('page', String(params.page))
  if (params.size != null) search.set('size', String(params.size))
  if (params.status) search.set('status', params.status)
  if (params.type) search.set('type', params.type)
  if (params.q) search.set('q', params.q)
  const text = search.toString()
  return text ? `?${text}` : ''
}

export type PlainInvitationCode = {
  id: string
  codeHint: string
  plainCode: string
}

export const invitationsApi = {
  list: (params: InvitationQuery = {}) =>
    requestJson<InvitationPage>(`/api/admin/v1/invitations${toQuery(params)}`),
  stats: () => requestJson<InvitationStats>('/api/admin/v1/invitations/stats'),
  create: (payload: CreateInvitationPayload) =>
    requestJson<CreateInvitationResponse>('/api/admin/v1/invitations', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  remove: (id: string) =>
    requestJson<void>(`/api/admin/v1/invitations/${id}`, { method: 'DELETE' }),
  plainCode: (id: string) =>
    requestJson<PlainInvitationCode>(`/api/admin/v1/invitations/${id}/plain-code`),
}
