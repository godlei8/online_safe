import { defineStore } from 'pinia'
import { authApi, type LoginPayload, type RegistrationPayload, type Session } from '@/api/auth'
import { refreshCsrfToken } from '@/api/client'

const anonymousSession: Session = { authenticated: false, userId: null, username: null, role: null }

export const useAuthStore = defineStore('auth', {
  state: () => ({ session: anonymousSession as Session, ready: false }),
  actions: {
    async bootstrap() {
      await refreshCsrfToken()
      this.session = await authApi.currentSession()
      this.ready = true
    },
    async login(payload: LoginPayload) {
      this.session = await authApi.login(payload)
    },
    async register(payload: RegistrationPayload) {
      const registration = await authApi.register(payload)
      await refreshCsrfToken()
      return registration
    },
    async logout() {
      await authApi.logout()
      this.session = anonymousSession
      await refreshCsrfToken()
    },
  },
})
