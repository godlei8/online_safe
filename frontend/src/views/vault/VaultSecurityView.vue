<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiRequestError } from '@/api/client'
import { userSessionsApi, type UserSession } from '@/api/userSessions'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'
import { publishAuthBroadcast } from '@/composables/useAuthBroadcast'
import { useOsToast } from '@/composables/useOsToast'
import { useAuthStore } from '@/stores/auth'
import { useVaultStore } from '@/stores/vault'

const router = useRouter()
const auth = useAuthStore()
const vault = useVaultStore()
const toast = useOsToast()

const loading = ref(true)
const loadError = ref('')
const sessions = ref<UserSession[]>([])
const maxActiveSessions = ref(2)
const acting = ref(false)

const revokeOneOpen = ref(false)
const revokeOthersOpen = ref(false)
const revokeAllOpen = ref(false)
const logoutCurrentOpen = ref(false)
const pendingRevokeId = ref<string | null>(null)

const currentSession = computed(() => sessions.value.find((item) => item.current) ?? null)
const otherSessions = computed(() => sessions.value.filter((item) => !item.current))
const onlyCurrent = computed(() => !loading.value && !loadError.value && otherSessions.value.length === 0 && !!currentSession.value)

function deviceIcon(deviceType: string | null | undefined) {
  const value = (deviceType || '').toUpperCase()
  if (value.includes('MOBILE') || value.includes('PHONE')) return 'mdi-cellphone'
  if (value.includes('TABLET')) return 'mdi-tablet'
  if (value.includes('DESKTOP') || value.includes('COMPUTER')) return 'mdi-monitor'
  return 'mdi-devices'
}

function formatTime(value: string | null | undefined) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await userSessionsApi.list()
    sessions.value = result.sessions
    maxActiveSessions.value = result.maxActiveSessions
  } catch (error) {
    loadError.value = error instanceof ApiRequestError ? error.message : '加载登录设备失败'
  } finally {
    loading.value = false
  }
}

function askRevokeOne(id: string) {
  pendingRevokeId.value = id
  revokeOneOpen.value = true
}

async function confirmRevokeOne() {
  if (!pendingRevokeId.value || acting.value) return
  acting.value = true
  try {
    await userSessionsApi.revokeOne(pendingRevokeId.value)
    revokeOneOpen.value = false
    pendingRevokeId.value = null
    toast.success('已退出该设备')
    await load()
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '退出设备失败')
  } finally {
    acting.value = false
  }
}

async function confirmRevokeOthers() {
  if (acting.value) return
  acting.value = true
  try {
    const result = await userSessionsApi.revokeOthers()
    revokeOthersOpen.value = false
    toast.success(`已退出其他 ${result.revokedCount} 个设备`)
    await load()
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '退出其他设备失败')
  } finally {
    acting.value = false
  }
}

async function confirmLogoutCurrent() {
  if (acting.value) return
  acting.value = true
  try {
    await auth.logout()
    publishAuthBroadcast({ type: 'LOGOUT' })
    logoutCurrentOpen.value = false
    await router.replace('/login')
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '退出失败')
  } finally {
    acting.value = false
  }
}

async function confirmRevokeAll() {
  if (acting.value) return
  acting.value = true
  try {
    await userSessionsApi.revokeAll()
    vault.clearSessionData()
    auth.$patch({
      session: { authenticated: false, userId: null, username: null, role: null, avatarUrl: null },
      ready: true,
    })
    publishAuthBroadcast({ type: 'SESSION_REVOKED' })
    revokeAllOpen.value = false
    await router.replace('/login')
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '退出全部设备失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="vault-content vault-security vault-page">
    <div class="vault-page__chrome">
    <div class="vault-title-row">
      <div class="vault-title-row__heading">
        <h1>安全中心</h1>
        <span>查看并管理当前账号的登录设备。</span>
      </div>
      <div class="vault-security__actions">
        <v-btn
          v-if="otherSessions.length > 0"
          class="vault-security__btn"
          variant="outlined"
          color="primary"
          prepend-icon="mdi-monitor-off"
          :disabled="loading || acting"
          @click="revokeOthersOpen = true"
        >
          退出其他设备
        </v-btn>
        <v-btn
          class="vault-security__btn vault-security__btn--danger"
          variant="flat"
          color="error"
          prepend-icon="mdi-logout-variant"
          :disabled="loading || acting"
          @click="revokeAllOpen = true"
        >
          退出全部设备
        </v-btn>
      </div>
    </div>

    <p class="vault-privacy-note" role="note">
      <v-icon icon="mdi-shield-lock-outline" size="16" />
      <span>最多可同时保持 {{ maxActiveSessions }} 台设备登录；超出后将挤掉最久未用的设备。</span>
    </p>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />
    </div>

    <div class="vault-page__body">
    <div v-if="!loading && loadError" class="vault-security__error">
      <p>{{ loadError }}</p>
      <v-btn variant="outlined" color="primary" @click="load">重新加载</v-btn>
    </div>

    <template v-else-if="!loading">
      <div class="vault-security__grid">
        <article v-if="currentSession" class="vault-security__card vault-security__card--current">
          <header class="vault-security__card-head">
            <span class="vault-security__icon" aria-hidden="true">
              <v-icon :icon="deviceIcon(currentSession.deviceType)" size="20" />
            </span>
            <div class="vault-security__head-text">
              <strong>{{ currentSession.displayName || '当前设备' }}</strong>
              <span class="vault-security__badge">当前设备</span>
            </div>
          </header>
          <dl class="vault-security__facts">
            <div><dt>登录时间</dt><dd>{{ formatTime(currentSession.loginAt) }}</dd></div>
            <div><dt>最近活动</dt><dd>{{ formatTime(currentSession.lastActiveAt) }}</dd></div>
            <div><dt>预计失效</dt><dd>{{ formatTime(currentSession.expiresAt) }}</dd></div>
            <div><dt>登录网段</dt><dd>{{ currentSession.ipMasked || '—' }}</dd></div>
          </dl>
          <footer class="vault-security__card-foot">
            <v-btn
              class="vault-security__btn vault-security__btn--current"
              variant="tonal"
              color="primary"
              prepend-icon="mdi-logout"
              :disabled="acting"
              block
              @click="logoutCurrentOpen = true"
            >
              退出当前设备
            </v-btn>
          </footer>
        </article>

        <article
          v-for="session in otherSessions"
          :key="session.id"
          class="vault-security__card"
        >
          <header class="vault-security__card-head">
            <span class="vault-security__icon" aria-hidden="true">
              <v-icon :icon="deviceIcon(session.deviceType)" size="20" />
            </span>
            <div class="vault-security__head-text">
              <strong>{{ session.displayName || '未知设备' }}</strong>
            </div>
          </header>
          <dl class="vault-security__facts">
            <div><dt>登录时间</dt><dd>{{ formatTime(session.loginAt) }}</dd></div>
            <div><dt>最近活动</dt><dd>{{ formatTime(session.lastActiveAt) }}</dd></div>
            <div><dt>预计失效</dt><dd>{{ formatTime(session.expiresAt) }}</dd></div>
            <div><dt>登录网段</dt><dd>{{ session.ipMasked || '—' }}</dd></div>
          </dl>
          <footer class="vault-security__card-foot">
            <v-btn
              class="vault-security__btn vault-security__btn--revoke"
              variant="tonal"
              color="error"
              prepend-icon="mdi-link-off"
              :disabled="acting"
              block
              @click="askRevokeOne(session.id)"
            >
              退出该设备
            </v-btn>
          </footer>
        </article>
      </div>

      <div v-if="onlyCurrent" class="vault-security__solo">
        <v-icon icon="mdi-check-circle-outline" size="20" />
        <p>当前只有这一台登录设备。</p>
      </div>
    </template>
    </div>

    <OsConfirmDialog
      v-model="revokeOneOpen"
      variant="warning"
      title="退出该设备？"
      message="该设备需要重新登录后才能继续访问保险箱。"
      confirm-text="确认退出"
      :loading="acting"
      @confirm="confirmRevokeOne"
    />
    <OsConfirmDialog
      v-model="revokeOthersOpen"
      variant="warning"
      title="退出其他设备？"
      :message="`将退出其他 ${otherSessions.length} 个设备，当前设备保持登录。`"
      confirm-text="确认退出"
      :loading="acting"
      @confirm="confirmRevokeOthers"
    />
    <OsConfirmDialog
      v-model="logoutCurrentOpen"
      variant="warning"
      title="退出当前设备？"
      message="退出后需重新登录才能继续使用保险箱。"
      confirm-text="确认退出"
      :loading="acting"
      @confirm="confirmLogoutCurrent"
    />
    <OsConfirmDialog
      v-model="revokeAllOpen"
      variant="danger"
      title="退出全部设备？"
      message="将退出包括当前页面在内的全部登录设备，操作后需重新登录。"
      confirm-text="全部退出"
      :loading="acting"
      @confirm="confirmRevokeAll"
    />
  </section>
</template>

<style scoped>
.vault-security__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 0 1 auto;
  min-width: 0;
  justify-content: flex-end;
}

.vault-security__btn {
  --v-btn-height: var(--os-control-md);
  min-height: var(--os-control-md) !important;
  height: var(--os-control-md) !important;
  padding-inline: 12px !important;
  border-radius: 6px !important;
  font-size: 0.8125rem !important;
  font-weight: 600;
  letter-spacing: 0;
}

.vault-security__btn .v-icon {
  font-size: 16px !important;
  opacity: 0.92;
}

.vault-security__btn--current {
  box-shadow: 0 1px 2px rgb(21 94 239 / 12%);
}

.vault-security__btn--current:hover {
  box-shadow: 0 2px 6px rgb(21 94 239 / 18%);
}

.vault-security__btn--revoke {
  box-shadow: 0 1px 2px rgb(180 35 24 / 10%);
}

.vault-security__btn--revoke:hover {
  box-shadow: 0 2px 6px rgb(180 35 24 / 16%);
}

.vault-security__btn--danger {
  box-shadow: 0 1px 2px rgb(180 35 24 / 18%);
}

.vault-security__btn--danger:hover {
  box-shadow: 0 2px 6px rgb(180 35 24 / 22%);
}

.vault-security :deep(.vault-title-row) {
  min-width: 0;
  justify-content: flex-start;
}

.vault-security :deep(.vault-title-row__heading) {
  flex: 1 1 auto;
  min-width: 0;
}

@media (max-width: 599px) {
  .vault-security :deep(.vault-title-row__heading) {
    flex: 0 0 auto;
    width: 100%;
  }

  .vault-security__actions {
    width: 100%;
    justify-content: stretch;
  }

  .vault-security__actions .vault-security__btn {
    flex: 1 1 calc(50% - 4px);
  }
}

.vault-security__error {
  display: grid;
  gap: 12px;
  justify-items: start;
  padding: 20px;
  border: 1px solid var(--os-border);
  border-radius: var(--os-radius-card);
  background: var(--os-surface);
}

.vault-security__error p {
  margin: 0;
  color: var(--os-text-muted);
}

.vault-security__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: stretch;
}

.vault-security__card {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--os-border);
  border-radius: var(--os-radius-card);
  background: var(--os-surface);
  box-shadow: var(--os-shadow-1);
}

.vault-security__card--current {
  border-color: color-mix(in srgb, var(--os-primary) 32%, var(--os-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--os-primary) 8%, transparent), var(--os-shadow-1);
}

.vault-security__card-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 14px 12px;
}

.vault-security__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: var(--os-primary);
  background: var(--os-primary-tint);
  flex: 0 0 auto;
}

.vault-security__head-text {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  min-width: 0;
  flex: 1 1 auto;
  padding-top: 2px;
}

.vault-security__head-text strong {
  color: var(--os-text-title);
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1.35;
  word-break: break-word;
}

.vault-security__badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 650;
  line-height: 1.4;
  color: var(--os-primary);
  background: color-mix(in srgb, var(--os-primary) 12%, transparent);
}

.vault-security__facts {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  margin: 0 14px;
  padding: 8px 10px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--os-bg) 88%, var(--os-surface));
}

.vault-security__facts > div {
  display: grid;
  grid-template-columns: 4.25rem minmax(0, 1fr);
  column-gap: 10px;
  align-items: baseline;
  padding: 5px 0;
}

.vault-security__facts > div + div {
  border-top: 1px solid color-mix(in srgb, var(--os-border) 80%, transparent);
}

.vault-security__facts dt {
  color: var(--os-text-muted);
  font-size: 0.71875rem;
  font-weight: 500;
}

.vault-security__facts dd {
  margin: 0;
  color: var(--os-text-body);
  font-size: 0.78125rem;
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
  word-break: break-all;
}

.vault-security__card-foot {
  margin-top: auto;
  padding: 12px 14px 14px;
}

.vault-security__solo {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: var(--os-radius-card);
  color: var(--os-success, #15803d);
  background: color-mix(in srgb, var(--os-success, #15803d) 10%, transparent);
}

.vault-security__solo p {
  margin: 0;
  font-weight: 600;
}

@media (max-width: 1100px) {
  .vault-security__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .vault-security__actions {
    width: 100%;
  }

  .vault-security__actions .vault-security__btn {
    flex: 1 1 auto;
  }

  .vault-security__grid {
    grid-template-columns: 1fr;
  }
}
</style>
