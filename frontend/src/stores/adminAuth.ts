import { defineStore } from 'pinia'
import { adminAuthApi, type AdminLoginPayload, type AdminSession } from '@/api/adminAuth'
import { refreshCsrfToken } from '@/api/client'

const anonymousSession: AdminSession = {
  authenticated: false,
  adminId: null,
  username: null,
  role: null,
}

export const useAdminAuthStore = defineStore('adminAuth', {
  state: () => ({
    session: anonymousSession as AdminSession,
    ready: false,
  }),
  actions: {
    async bootstrap() {
      await refreshCsrfToken()
      this.session = await adminAuthApi.currentSession()
      this.ready = true
    },
    async login(payload: AdminLoginPayload) {
      this.session = await adminAuthApi.login(payload)
      await refreshCsrfToken()
    },
    async logout() {
      await adminAuthApi.logout()
      this.session = anonymousSession
      await refreshCsrfToken()
    },
  },
})
