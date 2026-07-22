import { requestJson } from './client'

export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export type AdminAnnouncement = {
  id: string
  title: string
  body: string
  status: AnnouncementStatus
  pinned: boolean
  startsAt: string | null
  endsAt: string | null
  publishedAt: string | null
  createdByAdminId: string
  createdAt: string
  updatedAt: string
}

export type AnnouncementPage = {
  content: AdminAnnouncement[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type AnnouncementUpsertPayload = {
  title: string
  body: string
  pinned: boolean
  startsAt?: string | null
  endsAt?: string | null
}

export type UserAnnouncement = {
  id: string
  title: string
  body: string
  pinned: boolean
  startsAt: string | null
  endsAt: string | null
  publishedAt: string | null
  read: boolean
}

export type AnnouncementInbox = {
  unreadCount: number
  latestUnread: UserAnnouncement | null
  pinned: UserAnnouncement | null
}

export type AdminAnnouncementQuery = {
  page?: number
  size?: number
  status?: AnnouncementStatus | ''
}

function toAdminQuery(params: AdminAnnouncementQuery) {
  const search = new URLSearchParams()
  if (params.page != null) search.set('page', String(params.page))
  if (params.size != null) search.set('size', String(params.size))
  if (params.status) search.set('status', params.status)
  const text = search.toString()
  return text ? `?${text}` : ''
}

function toIsoOrNull(localValue: string) {
  const trimmed = localValue.trim()
  if (!trimmed) return null
  const date = new Date(trimmed)
  if (Number.isNaN(date.getTime())) return null
  return date.toISOString()
}

export function localInputFromIso(iso: string | null | undefined) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function buildUpsertPayload(input: {
  title: string
  body: string
  pinned: boolean
  startsAtLocal: string
  endsAtLocal: string
}): AnnouncementUpsertPayload {
  return {
    title: input.title.trim(),
    body: input.body.trim(),
    pinned: input.pinned,
    startsAt: toIsoOrNull(input.startsAtLocal),
    endsAt: toIsoOrNull(input.endsAtLocal),
  }
}

export const adminAnnouncementsApi = {
  list: (params: AdminAnnouncementQuery = {}) =>
    requestJson<AnnouncementPage>(`/api/admin/v1/announcements${toAdminQuery(params)}`),
  create: (payload: AnnouncementUpsertPayload) =>
    requestJson<AdminAnnouncement>('/api/admin/v1/announcements', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  update: (id: string, payload: AnnouncementUpsertPayload) =>
    requestJson<AdminAnnouncement>(`/api/admin/v1/announcements/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  publish: (id: string) =>
    requestJson<AdminAnnouncement>(`/api/admin/v1/announcements/${id}/publish`, {
      method: 'POST',
    }),
  offline: (id: string) =>
    requestJson<AdminAnnouncement>(`/api/admin/v1/announcements/${id}/offline`, {
      method: 'POST',
    }),
}

export const announcementsApi = {
  inbox: () => requestJson<AnnouncementInbox>('/api/v1/announcements/inbox'),
  list: () => requestJson<UserAnnouncement[]>('/api/v1/announcements'),
  markRead: (id: string) =>
    requestJson<void>(`/api/v1/announcements/${id}/read`, { method: 'POST' }),
}
