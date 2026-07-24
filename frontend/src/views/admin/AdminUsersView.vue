<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { ApiRequestError } from '@/api/client'
import { usersApi, type ManagedUser, type ManagedUserStats } from '@/api/users'
import AdminEllipsisText from '@/components/AdminEllipsisText.vue'
import AdminFilterSheet from '@/components/AdminFilterSheet.vue'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'
import OsHintBar from '@/components/OsHintBar.vue'
import { useMobileInfiniteScroll } from '@/composables/useMobileInfiniteScroll'

type UserConfirmAction = 'disable' | 'enable' | 'revokeSessions'

const { xs } = useDisplay()
const filtersOpen = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const acting = ref(false)
const errorMessage = ref('')
const toast = ref(false)
const toastText = ref('')
const items = ref<ManagedUser[]>([])
const stats = ref<ManagedUserStats>({ total: 0, active: 0, disabled: 0, activeLast7Days: 0 })
const page = ref(1)
const pageSize = 10
const totalElements = ref(0)
const query = ref('')
const statusFilter = ref('')
const registeredWithin = ref('30d')

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize)))
const hasMore = computed(() => items.value.length < totalElements.value)
const activeFilterCount = computed(() => (statusFilter.value ? 1 : 0) + (registeredWithin.value ? 1 : 0))

function resetFilters() {
  statusFilter.value = ''
  registeredWithin.value = ''
}

const statusOptions = [
  { title: '状态：全部', value: '' },
  { title: '正常', value: 'ACTIVE' },
  { title: '已禁用', value: 'DISABLED' },
]

const registeredOptions = [
  { title: '注册时间：全部', value: '' },
  { title: '近 7 天', value: '7d' },
  { title: '近 30 天', value: '30d' },
  { title: '近 90 天', value: '90d' },
]

onMounted(() => { void loadData() })
watch([statusFilter, registeredWithin], () => {
  page.value = 1
  void loadData()
})
watch(page, () => {
  if (!xs.value) void loadData()
})

async function loadData(options?: { append?: boolean }) {
  const append = Boolean(options?.append) && xs.value
  if (append) {
    if (loadingMore.value || loading.value) return
    loadingMore.value = true
  } else {
    loading.value = true
  }
  errorMessage.value = ''
  try {
    const listParams = {
      page: page.value - 1,
      size: pageSize,
      q: query.value.trim() || undefined,
      status: statusFilter.value || undefined,
      registeredWithin: registeredWithin.value || undefined,
    }
    if (append) {
      const listResult = await usersApi.list(listParams)
      items.value = [...items.value, ...listResult.content]
      totalElements.value = listResult.totalElements
    } else {
      const [statsResult, listResult] = await Promise.all([
        usersApi.stats(),
        usersApi.list(listParams),
      ])
      stats.value = statsResult
      items.value = listResult.content
      totalElements.value = listResult.totalElements
    }
  } catch (error) {
    if (append) page.value = Math.max(1, page.value - 1)
    errorMessage.value = error instanceof ApiRequestError ? error.message : '加载用户失败，请稍后重试。'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMore() {
  if (!xs.value || !hasMore.value) return
  page.value += 1
  await loadData({ append: true })
}

const { scrollEl } = useMobileInfiniteScroll({
  enabled: xs,
  loading,
  loadingMore,
  hasMore,
  loadMore,
})

async function search() {
  page.value = 1
  await loadData()
}

async function reload() {
  page.value = 1
  await loadData()
}

function statusLabel(status: string) {
  return status === 'ACTIVE' ? '正常' : status === 'DISABLED' ? '已禁用' : status
}

function statusColor(status: string) {
  return status === 'ACTIVE' ? 'success' : 'error'
}

function formatTime(value: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatRelative(value: string | null) {
  if (!value) return '—'
  const diffMs = Date.now() - new Date(value).getTime()
  if (Number.isNaN(diffMs)) return '—'
  const minutes = Math.floor(diffMs / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return formatTime(value)
}

function formatBytes(value: number | null) {
  if (value == null) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

const confirmOpen = ref(false)
const confirmAction = ref<UserConfirmAction | null>(null)
const confirmUser = ref<ManagedUser | null>(null)

const confirmTitle = computed(() => {
  if (confirmAction.value === 'disable') return '确认禁用用户？'
  if (confirmAction.value === 'enable') return '确认启用用户？'
  if (confirmAction.value === 'revokeSessions') return '确认使会话失效？'
  return '确认操作？'
})

const confirmMessage = computed(() => {
  const name = confirmUser.value?.username || '该用户'
  if (confirmAction.value === 'disable') {
    return `将禁用用户「${name}」，并同时使其现有会话失效。是否继续？`
  }
  if (confirmAction.value === 'enable') {
    return `将启用用户「${name}」。是否继续？`
  }
  if (confirmAction.value === 'revokeSessions') {
    return `将使用户「${name}」的全部登录会话失效。是否继续？`
  }
  return '是否继续？'
})

const confirmText = computed(() => {
  if (confirmAction.value === 'disable') return '确认禁用'
  if (confirmAction.value === 'enable') return '确认启用'
  if (confirmAction.value === 'revokeSessions') return '确认失效'
  return '确认'
})

const confirmVariant = computed(() => (
  confirmAction.value === 'enable' ? 'primary' : 'danger'
))

function askUserAction(action: UserConfirmAction, user: ManagedUser) {
  confirmAction.value = action
  confirmUser.value = user
  confirmOpen.value = true
}

async function confirmUserAction() {
  if (!confirmUser.value || !confirmAction.value) return
  const user = confirmUser.value
  const action = confirmAction.value
  let ok = false
  if (action === 'disable') {
    ok = await runAction(() => usersApi.disable(user.id), '已禁用用户')
  } else if (action === 'enable') {
    ok = await runAction(() => usersApi.enable(user.id), '已启用用户')
  } else {
    ok = await runAction(() => usersApi.revokeSessions(user.id), '已使会话失效')
  }
  if (!ok) return
  confirmOpen.value = false
  confirmAction.value = null
  confirmUser.value = null
}

async function runAction(action: () => Promise<ManagedUser>, successText: string) {
  acting.value = true
  errorMessage.value = ''
  try {
    await action()
    toastText.value = successText
    toast.value = true
    await loadData()
    return true
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '操作失败，请稍后重试。'
    return false
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <div class="admin-users admin-page">
    <div class="admin-page__chrome">
    <div class="admin-stat-grid mb-4">
      <v-card class="admin-stat-card admin-stat-card--primary" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">用户总数</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-account-group-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.total }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--success" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">正常</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-account-check-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.active }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--error" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">已禁用</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-account-off-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.disabled }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--warning" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">近 7 日活跃</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-chart-timeline-variant" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.activeLast7Days }}</div>
      </v-card>
    </div>

    <OsHintBar class="mb-4">
      管理员仅可见账户元数据，不可查看用户设备详情或保险箱明文。可通过「活跃会话」列查看数量，并用「使会话失效」踢掉该用户全部登录。个人会话上限在「系统设置 → 登录会话」配置。
    </OsHintBar>

    <v-card class="admin-panel mb-4" elevation="0">
      <div class="admin-filter-row">
        <v-text-field
          v-model="query"
          class="admin-filter-field"
          density="compact"
          hide-details
          placeholder="按手机号或用户名搜索"
          prepend-inner-icon="mdi-magnify"
          @keyup.enter="search"
        />
        <template v-if="!xs">
          <v-select
            v-model="statusFilter"
            class="admin-filter-select"
            density="compact"
            hide-details
            :items="statusOptions"
            item-title="title"
            item-value="value"
          />
          <v-select
            v-model="registeredWithin"
            class="admin-filter-select"
            density="compact"
            hide-details
            :items="registeredOptions"
            item-title="title"
            item-value="value"
          />
          <div class="admin-filter-actions">
            <v-btn class="admin-toolbar-btn" variant="tonal" color="primary" @click="search">查询</v-btn>
            <v-btn
              class="admin-toolbar-btn"
              variant="outlined"
              color="primary"
              prepend-icon="mdi-refresh"
              :loading="loading"
              @click="reload"
            >
              刷新
            </v-btn>
          </div>
        </template>
        <template v-else>
          <div class="admin-filter-actions admin-filter-actions--tools">
            <v-btn
              class="admin-toolbar-btn admin-filter-trigger"
              variant="outlined"
              color="primary"
              prepend-icon="mdi-filter-variant"
              :aria-label="activeFilterCount ? `筛选，已选 ${activeFilterCount} 项` : '筛选'"
              @click="filtersOpen = true"
            >
              筛选
              <span v-if="activeFilterCount" class="admin-filter-trigger__badge" aria-hidden="true">{{ activeFilterCount }}</span>
            </v-btn>
            <v-btn
              class="admin-toolbar-btn admin-filter-trigger"
              variant="outlined"
              color="primary"
              icon="mdi-refresh"
              aria-label="刷新"
              :loading="loading"
              @click="reload"
            />
          </div>
        </template>
      </div>
    </v-card>

    <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4">{{ errorMessage }}</v-alert>
    </div>

    <AdminFilterSheet v-model="filtersOpen" @apply="search" @reset="resetFilters">
      <v-select
        v-model="statusFilter"
        hide-details
        :items="statusOptions"
        item-title="title"
        item-value="value"
        label="账户状态"
      />
      <v-select
        v-model="registeredWithin"
        hide-details
        :items="registeredOptions"
        item-title="title"
        item-value="value"
        label="注册时间"
      />
    </AdminFilterSheet>

    <div class="admin-page__table">
    <v-card class="admin-panel admin-page__table-panel" elevation="0">
      <div ref="scrollEl" class="admin-page__scroll">
      <v-table class="admin-table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>手机号</th>
            <th>账户状态</th>
            <th>注册时间</th>
            <th>最近登录</th>
            <th>密文存储</th>
            <th>记录数</th>
            <th>活跃会话</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && items.length === 0">
            <td colspan="9" class="text-medium-emphasis py-8 text-center">暂无匹配的用户。</td>
          </tr>
          <tr
            v-for="item in items"
            :key="item.id"
            :class="{ 'admin-table__row--disabled': item.status === 'DISABLED' }"
          >
            <td data-label="用户名">
              <AdminEllipsisText
                class="font-weight-medium"
                :text="item.username"
                max-width="10rem"
              />
            </td>
            <td data-label="手机号">
              <AdminEllipsisText :text="item.maskedPhone" max-width="8rem" />
            </td>
            <td data-label="账户状态">
              <span class="admin-status-text" :data-tone="statusColor(item.status)">
                {{ statusLabel(item.status) }}
              </span>
            </td>
            <td data-label="注册时间" data-mobile-hide>
              <AdminEllipsisText :text="formatTime(item.createdAt)" max-width="9rem" />
            </td>
            <td data-label="最近登录">
              <AdminEllipsisText :text="formatRelative(item.lastLoginAt)" max-width="8rem" />
            </td>
            <td data-label="密文存储" data-mobile-hide>
              <AdminEllipsisText :text="formatBytes(item.cipherStorageBytes)" max-width="6rem" />
            </td>
            <td data-label="记录数">{{ item.recordCount ?? '—' }}</td>
            <td data-label="活跃会话">{{ item.activeSessionCount }}</td>
            <td data-label="操作">
              <div class="admin-row-actions">
                <v-btn
                  v-if="item.status === 'ACTIVE'"
                  class="admin-row-actions__btn admin-row-actions__btn--danger"
                  size="x-small"
                  variant="text"
                  color="error"
                  prepend-icon="mdi-account-off-outline"
                  :disabled="acting"
                  @click="askUserAction('disable', item)"
                >
                  禁用
                </v-btn>
                <v-btn
                  v-else
                  class="admin-row-actions__btn admin-row-actions__btn--primary"
                  size="x-small"
                  variant="flat"
                  color="primary"
                  prepend-icon="mdi-account-check-outline"
                  :disabled="acting"
                  @click="askUserAction('enable', item)"
                >
                  启用
                </v-btn>
                <v-btn
                  class="admin-row-actions__btn"
                  size="x-small"
                  variant="text"
                  color="primary"
                  prepend-icon="mdi-logout-variant"
                  :disabled="acting"
                  @click="askUserAction('revokeSessions', item)"
                >
                  使会话失效
                </v-btn>
              </div>
            </td>
          </tr>
        </tbody>
      </v-table>
      </div>

      <div class="admin-pagination-row admin-page__pager">
        <template v-if="xs">
          <p class="admin-page__infinite-status">
            <span v-if="loadingMore">已加载 {{ items.length }} / {{ totalElements }} · 加载中…</span>
            <span v-else-if="items.length > 0 && !hasMore">共 {{ totalElements }} 条 · 已全部加载</span>
            <span v-else-if="hasMore">已加载 {{ items.length }} / {{ totalElements }} · 上滑加载更多</span>
            <span v-else>共 {{ totalElements }} 条</span>
          </p>
        </template>
        <template v-else>
          <div class="text-caption text-medium-emphasis">共 {{ totalElements }} 条，第 {{ page }} / {{ totalPages }} 页</div>
          <v-pagination v-model="page" :length="totalPages" total-visible="5" />
        </template>
      </div>
    </v-card>
    </div>

    <OsConfirmDialog
      v-model="confirmOpen"
      :variant="confirmVariant"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-text="confirmText"
      :loading="acting"
      @confirm="confirmUserAction"
    />

    <v-snackbar v-model="toast" color="success" timeout="2400" location="top">{{ toastText }}</v-snackbar>
  </div>
</template>
