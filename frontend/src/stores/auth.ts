import { defineStore } from 'pinia'
import { authApi, type LoginPayload, type RegistrationPayload, type Session } from '@/api/auth'
import { refreshCsrfToken } from '@/api/client'
import { useVaultStore } from '@/stores/vault'
import { useAnnouncementsStore } from '@/stores/announcements'

const anonymousSession: Session = {
  authenticated: false,
  userId: null,
  username: null,
  role: null,
  avatarUrl: null,
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ session: anonymousSession as Session, ready: false }),
  actions: {
    normalizeSession(session: Session): Session {
      return {
        ...session,
        avatarUrl: session.avatarUrl ?? null,
      }
    },
    async bootstrap() {
      await refreshCsrfToken()
      this.session = this.normalizeSession(await authApi.currentSession())
      this.ready = true
    },
    async login(payload: LoginPayload) {
      this.session = this.normalizeSession(await authApi.login(payload))
      const vault = useVaultStore()
      vault.clearSessionData()
      useAnnouncementsStore().clear()
      await vault.refreshInitialization()
    },
    async register(payload: RegistrationPayload) {
      const registration = await authApi.register(payload)
      await refreshCsrfToken()
      return registration
    },
    async logout() {
      useVaultStore().clearSessionData()
      useAnnouncementsStore().clear()
      await authApi.logout()
      this.session = anonymousSession
      await refreshCsrfToken()
    },
    patchProfile(partial: { username?: string; avatarUrl?: string | null }) {
      this.session = {
        ...this.session,
        username: partial.username ?? this.session.username,
        avatarUrl: partial.avatarUrl !== undefined ? partial.avatarUrl : this.session.avatarUrl,
      }
    },
  },
})
