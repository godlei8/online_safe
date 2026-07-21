import { requestJson } from './client'

export type Session = { authenticated: boolean; userId: string | null; username: string | null; role: string | null }
export type RegistrationResponse = { id: string; username: string; maskedPhone: string; phoneVerified: boolean; createdAt: string }
export type LoginPayload = { identifier: string; password: string }
export type SecurityQuestionPayload = {
  questionType: 'BUILTIN' | 'CUSTOM'
  questionCode?: string
  questionText?: string
  answer: string
}
export type RegistrationPayload = {
  phone: string
  username: string
  password: string
  confirmPassword: string
  invitationCode: string
  securityQuestions: SecurityQuestionPayload[]
}
export type BuiltinSecurityQuestion = { code: string; text: string }
export type PasswordResetQuestion = {
  questionId: string
  questionType: string
  questionCode: string | null
  questionText: string
  sortOrder: number
}

export const authApi = {
  currentSession: () => requestJson<Session>('/api/auth/session'),
  login: (payload: LoginPayload) => requestJson<Session>('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  register: (payload: RegistrationPayload) => requestJson<RegistrationResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  logout: () => requestJson<void>('/api/auth/logout', { method: 'POST' }),
  listBuiltinSecurityQuestions: () => requestJson<BuiltinSecurityQuestion[]>('/api/auth/security-questions/builtins'),
  lookupPasswordReset: (identifier: string) =>
    requestJson<{ questions: PasswordResetQuestion[] }>('/api/auth/password-reset/lookup', {
      method: 'POST',
      body: JSON.stringify({ identifier }),
    }),
  confirmPasswordReset: (payload: {
    identifier: string
    answers: string[]
    newPassword: string
    confirmPassword: string
  }) =>
    requestJson<void>('/api/auth/password-reset/confirm', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
}
