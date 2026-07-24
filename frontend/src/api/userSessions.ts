import { requestJson } from '@/api/client'

export type UserSession = {
  id: string
  current: boolean
  deviceType: string
  browserFamily: string
  osFamily: string
  displayName: string
  ipMasked: string
  loginAt: string
  lastActiveAt: string
  expiresAt: string
}

export type UserSessionList = {
  activeCount: number
  maxActiveSessions: number
  sessions: UserSession[]
}

export type RevokeOthersResult = {
  revokedCount: number
}

export const userSessionsApi = {
  list: () => requestJson<UserSessionList>('/api/v1/security/sessions'),
  revokeOne: (publicId: string) =>
    requestJson<void>(`/api/v1/security/sessions/${encodeURIComponent(publicId)}`, {
      method: 'DELETE',
    }),
  revokeOthers: () =>
    requestJson<RevokeOthersResult>('/api/v1/security/sessions/revoke-others', {
      method: 'POST',
    }),
  revokeAll: () =>
    requestJson<void>('/api/v1/security/sessions/revoke-all', {
      method: 'POST',
    }),
}
