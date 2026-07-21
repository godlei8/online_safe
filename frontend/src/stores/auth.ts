import { defineStore } from 'pinia'
import { authApi, type LoginPayload, type RegistrationPayload, type Session } from '@/api/auth'
import { refreshCsrfToken } from '@/api/client'
import { useVaultStore } from '@/stores/vault'

const anonymousSession: Session = { authenticated: false, userId: null, username: null, role: null }

/**
 * 登录密码仅在内存短持有，用于登录后自动打开密钥信封；登出/失败时清除。
 * 不持久化、不写入 localStorage。
 */
let ephemeralLoginPassword: string | null = null

export function takeEphemeralLoginPassword(): string | null {
  const value = ephemeralLoginPassword
  ephemeralLoginPassword = null
  return value
}

export function peekEphemeralLoginPassword(): string | null {
  return ephemeralLoginPassword
}

export function setEphemeralLoginPassword(password: string | null) {
  ephemeralLoginPassword = password
}

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
      setEphemeralLoginPassword(payload.password)
      const vault = useVaultStore()
      vault.clearDek()
      vault.ready = false
      try {
        await vault.refreshInitialization()
        if (vault.initialized) {
          await vault.openWithPassword(payload.password)
          setEphemeralLoginPassword(null)
        }
      } catch {
        // 打开信封失败时保留临时密码，供首次初始化 setup 页使用
      }
    },
    async register(payload: RegistrationPayload) {
      const registration = await authApi.register(payload)
      await refreshCsrfToken()
      return registration
    },
    async logout() {
      useVaultStore().clearDek()
      setEphemeralLoginPassword(null)
      await authApi.logout()
      this.session = anonymousSession
      await refreshCsrfToken()
    },
  },
})
