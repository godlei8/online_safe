import { requestJson } from './client'

export type Session = {
  authenticated: boolean
  userId: string | null
  username: string | null
  role: string | null
  avatarUrl: string | null
}
export type RegistrationResponse = { id: string; username: string; maskedPhone: string; phoneVerified: boolean; createdAt: string }
export type LoginPayload = { identifier: string; password: string }
export type SmsPurpose = 'REGISTER' | 'RESET_PASSWORD'
export type RegistrationPayload = {
  phone: string
  smsCode: string
  username: string
  password: string
  confirmPassword: string
}

export const authApi = {
  currentSession: () => requestJson<Session>('/api/auth/session'),
  login: (payload: LoginPayload) => requestJson<Session>('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  register: (payload: RegistrationPayload) => requestJson<RegistrationResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  logout: () => requestJson<void>('/api/auth/logout', { method: 'POST' }),
  sendSms: (phone: string, purpose: SmsPurpose) =>
    requestJson<void>('/api/auth/sms/send', {
      method: 'POST',
      body: JSON.stringify({ phone, purpose }),
    }),
  confirmPasswordReset: (payload: {
    phone: string
    smsCode: string
    newPassword: string
    confirmPassword: string
  }) =>
    requestJson<void>('/api/auth/password-reset/confirm', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
}
