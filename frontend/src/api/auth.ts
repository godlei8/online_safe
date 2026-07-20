import { requestJson } from './client'

export type Session = { authenticated: boolean; userId: string | null; username: string | null; role: string | null }
export type RegistrationResponse = { id: string; username: string; maskedPhone: string; phoneVerified: boolean; createdAt: string }
export type LoginPayload = { identifier: string; password: string }
export type RegistrationPayload = { phone: string; username: string; password: string; confirmPassword: string; invitationCode: string }

export const authApi = {
  currentSession: () => requestJson<Session>('/api/auth/session'),
  login: (payload: LoginPayload) => requestJson<Session>('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  register: (payload: RegistrationPayload) => requestJson<RegistrationResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  logout: () => requestJson<void>('/api/auth/logout', { method: 'POST' }),
}
