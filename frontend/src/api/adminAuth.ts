import { requestJson } from './client'

export type AdminSession = {
  authenticated: boolean
  adminId: string | null
  username: string | null
  role: string | null
}

export type AdminLoginPayload = { username: string; password: string }

export const adminAuthApi = {
  currentSession: () => requestJson<AdminSession>('/api/admin/auth/session'),
  login: (payload: AdminLoginPayload) =>
    requestJson<AdminSession>('/api/admin/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  logout: () => requestJson<void>('/api/admin/auth/logout', { method: 'POST' }),
}
