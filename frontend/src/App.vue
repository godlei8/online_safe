<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useDisplay } from 'vuetify'
import { useRouter } from 'vue-router'
import OsToastHost from '@/components/OsToastHost.vue'
import { authApi } from '@/api/auth'
import {
  publishAuthBroadcast,
  subscribeAuthBroadcast,
  type AuthBroadcastMessage,
} from '@/composables/useAuthBroadcast'
import { useAuthStore } from '@/stores/auth'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useVaultStore } from '@/stores/vault'

const auth = useAuthStore()
const adminAuth = useAdminAuthStore()
const vault = useVaultStore()
const router = useRouter()
const { smAndDown } = useDisplay()
const initializationError = ref('')
const showInitializationError = ref(false)
const retryingConnection = ref(false)
const ready = ref(false)

let unsubscribeBroadcast: (() => void) | null = null
let sessionCheckTimer: ReturnType<typeof setInterval> | null = null
let checkingSession = false

async function initialize() {
  try {
    await Promise.all([auth.bootstrap(), adminAuth.bootstrap()])
    initializationError.value = ''
    showInitializationError.value = false
  } catch {
    initializationError.value = '暂时无法连接本地服务，部分操作可能不可用。'
    showInitializationError.value = true
    auth.ready = true
    adminAuth.ready = true
  } finally {
    ready.value = true
  }
}

async function retryConnection() {
  retryingConnection.value = true
  try {
    await initialize()
  } finally {
    retryingConnection.value = false
  }
}

function clearLocalUserSession() {
  vault.clearSessionData()
  auth.$patch({
    session: { authenticated: false, userId: null, username: null, role: null, avatarUrl: null },
    ready: true,
  })
}

async function onAuthBroadcast(message: AuthBroadcastMessage) {
  if (!auth.session.authenticated) return
  if (window.location.pathname.startsWith('/admin')) return
  clearLocalUserSession()
  const reason = message.type === 'SESSION_REVOKED' ? 'session-replaced' : undefined
  const query = reason ? { reason } : undefined
  if (router.currentRoute.value.path !== '/login') {
    await router.replace({ path: '/login', query })
  }
}

async function verifyUserSession() {
  if (checkingSession || !auth.session.authenticated) return
  if (document.visibilityState === 'hidden') return
  if (window.location.pathname.startsWith('/admin')) return
  if (window.location.pathname.startsWith('/login')) return
  checkingSession = true
  try {
    const session = await authApi.currentSession()
    if (!session.authenticated) {
      clearLocalUserSession()
      publishAuthBroadcast({ type: 'SESSION_REVOKED' })
      if (router.currentRoute.value.path !== '/login') {
        await router.replace({ path: '/login', query: { reason: 'session-replaced' } })
      }
    }
  } catch {
    // 网络抖动不强制登出
  } finally {
    checkingSession = false
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void verifyUserSession()
  }
}

onMounted(() => {
  void initialize()
  unsubscribeBroadcast = subscribeAuthBroadcast((message) => {
    void onAuthBroadcast(message)
  })
  document.addEventListener('visibilitychange', onVisibilityChange)
  sessionCheckTimer = setInterval(() => {
    void verifyUserSession()
  }, 5 * 60 * 1000)
})

onUnmounted(() => {
  unsubscribeBroadcast?.()
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (sessionCheckTimer) clearInterval(sessionCheckTimer)
})
</script>

<template>
  <v-app>
    <v-main>
      <router-view v-if="ready" />
      <div v-else class="d-flex align-center justify-center" style="min-height: 100vh">
        <v-progress-circular indeterminate color="primary" aria-label="正在初始化会话" />
      </div>
      <v-snackbar v-model="showInitializationError" color="warning" :location="smAndDown ? 'bottom' : 'top'" :timeout="-1" class="connection-snackbar">
        <div class="d-flex align-center">
          <v-icon icon="mdi-wifi-off" class="mr-2" />
          <span>{{ initializationError }}</span>
        </div>
        <template #actions>
          <v-btn variant="text" :loading="retryingConnection" @click="retryConnection">重新连接</v-btn>
          <v-btn icon="mdi-close" variant="text" aria-label="关闭提示" @click="showInitializationError = false" />
        </template>
      </v-snackbar>
      <OsToastHost />
    </v-main>
  </v-app>
</template>
