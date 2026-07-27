<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { ApiRequestError } from '@/api/client'
import { dataSecurityApi, type DataSecuritySummary, type TrashAsset } from '@/api/dataSecurity'
import BackupCreateDialog from '@/components/data-recovery/BackupCreateDialog.vue'
import BackupRestoreDialog from '@/components/data-recovery/BackupRestoreDialog.vue'
import VaultSecurityNav from '@/components/data-recovery/VaultSecurityNav.vue'
import AdminFilterSheet from '@/components/AdminFilterSheet.vue'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'
import { useMobileInfiniteScroll } from '@/composables/useMobileInfiniteScroll'
import { useOsToast } from '@/composables/useOsToast'

const { xs } = useDisplay()
const toast = useOsToast()

const summary = ref<DataSecuritySummary | null>(null)
const items = ref<TrashAsset[]>([])
const page = ref(0)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const keyword = ref('')
const typeFilter = ref<'ALL' | 'ITEM' | 'PRIVATE_TEMPLATE'>('ALL')
const filtersOpen = ref(false)
const createOpen = ref(false)
const restoreOpen = ref(false)

const restoreOneOpen = ref(false)
const purgeOneOpen = ref(false)
const purgeAllOpen = ref(false)
const reauthOpen = ref(false)
const reauthPassword = ref('')
const pendingAction = ref<'purgeOne' | 'purgeAll' | null>(null)
const pendingAsset = ref<TrashAsset | null>(null)
const acting = ref(false)

const hasMore = computed(() => page.value + 1 < totalPages.value)
const activeFilterCount = computed(() => (typeFilter.value === 'ALL' ? 0 : 1))
const infiniteEnabled = computed(() => xs.value)

const typeOptions = [
  { title: '全部类型', value: 'ALL' },
  { title: '账密记录', value: 'ITEM' },
  { title: '私人模板', value: 'PRIVATE_TEMPLATE' },
]

function formatTime(value: string | null | undefined) {
  if (!value) return '尚未创建'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function encryptionLabel(status: string | undefined) {
  if (status === 'HEALTHY') return '服务端加密正常'
  if (status === 'NEEDS_ATTENTION') return '部分数据需要处理'
  return '尚未检查'
}

async function loadSummary() {
  summary.value = await dataSecurityApi.summary()
}

async function loadTrash(reset = true) {
  if (reset) {
    loading.value = true
    page.value = 0
  } else {
    loadingMore.value = true
  }
  try {
    const result = await dataSecurityApi.listTrash({
      keyword: keyword.value,
      type: typeFilter.value,
      page: page.value,
      size: 20,
    })
    items.value = reset ? result.content : [...items.value, ...result.content]
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '加载回收站失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function refreshAll() {
  try {
    await Promise.all([loadSummary(), loadTrash(true)])
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '刷新失败')
  }
}

const { scrollEl } = useMobileInfiniteScroll({
  enabled: infiniteEnabled,
  loading,
  loadingMore,
  hasMore,
  loadMore: async () => {
    if (!hasMore.value) return
    page.value += 1
    await loadTrash(false)
  },
})

onMounted(() => {
  void refreshAll()
})

watch([keyword, typeFilter], () => {
  void loadTrash(true)
})

function askRestore(asset: TrashAsset) {
  pendingAsset.value = asset
  restoreOneOpen.value = true
}

function askPurgeOne(asset: TrashAsset) {
  pendingAsset.value = asset
  pendingAction.value = 'purgeOne'
  reauthPassword.value = ''
  reauthOpen.value = true
}

function askPurgeAll() {
  pendingAction.value = 'purgeAll'
  reauthPassword.value = ''
  reauthOpen.value = true
}

async function confirmReauth() {
  if (!reauthPassword.value || acting.value) return
  acting.value = true
  try {
    await dataSecurityApi.reauth(reauthPassword.value)
    reauthPassword.value = ''
    reauthOpen.value = false
    if (pendingAction.value === 'purgeOne') {
      purgeOneOpen.value = true
    } else if (pendingAction.value === 'purgeAll') {
      purgeAllOpen.value = true
    }
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '验证失败')
  } finally {
    acting.value = false
  }
}

async function confirmRestoreOne() {
  if (!pendingAsset.value || acting.value) return
  acting.value = true
  try {
    await dataSecurityApi.restoreTrash(pendingAsset.value.type, pendingAsset.value.id)
    restoreOneOpen.value = false
    toast.success('已恢复到保险箱')
    await refreshAll()
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '恢复失败')
  } finally {
    acting.value = false
  }
}

async function confirmPurgeOne() {
  if (!pendingAsset.value || acting.value) return
  acting.value = true
  try {
    await dataSecurityApi.purgeTrashOne(pendingAsset.value.type, pendingAsset.value.id)
    purgeOneOpen.value = false
    toast.success('已永久删除')
    await refreshAll()
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '删除失败')
  } finally {
    acting.value = false
  }
}

async function confirmPurgeAll() {
  if (acting.value) return
  acting.value = true
  try {
    const result = await dataSecurityApi.purgeTrashAll()
    purgeAllOpen.value = false
    toast.success(`已清空：记录 ${result.deletedItems}，模板 ${result.deletedTemplates}`)
    await refreshAll()
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '清空失败')
  } finally {
    acting.value = false
  }
}

async function runIntegrityScan() {
  acting.value = true
  try {
    const result = await dataSecurityApi.integrityScan()
    toast.success(result.status === 'HEALTHY' ? '完整性检查通过' : '发现需要处理的数据')
    await loadSummary()
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '完整性检查失败')
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <section class="vault-content vault-page vault-page--scroll vault-data-security">
    <div class="vault-title-row">
      <div class="vault-title-row__heading">
        <h1>安全中心</h1>
        <p class="vault-page-desc mb-0">管理登录设备与数据恢复能力。</p>
      </div>
    </div>

    <VaultSecurityNav />

    <div class="data-security-summary">
      <article class="data-security-card">
        <div class="data-security-card__main">
          <span class="data-security-card__label">加密状态</span>
          <strong class="data-security-card__value">{{ encryptionLabel(summary?.encryption.status) }}</strong>
        </div>
        <v-btn
          class="data-security-card__action"
          variant="text"
          color="primary"
          :loading="acting"
          @click="runIntegrityScan"
        >
          立即检查
        </v-btn>
      </article>
      <article class="data-security-card">
        <div class="data-security-card__main">
          <span class="data-security-card__label">最近备份</span>
          <strong class="data-security-card__value">{{ formatTime(summary?.backup.lastSnapshotAt) }}</strong>
        </div>
      </article>
      <article class="data-security-card">
        <div class="data-security-card__main">
          <span class="data-security-card__label">回收站</span>
          <strong class="data-security-card__value">
            {{ (summary?.trash.itemCount ?? 0) + (summary?.trash.templateCount ?? 0) }} 项待恢复
          </strong>
          <span v-if="summary?.trash.nearestPurgeAt" class="data-security-card__meta">
            最近到期 {{ formatTime(summary.trash.nearestPurgeAt) }}
          </span>
        </div>
      </article>
    </div>

    <section class="data-security-section">
      <div class="data-security-section__head data-security-section__head--row">
        <div class="data-security-section__meta">
          <h2>加密备份</h2>
          <p class="data-security-section__hint">独立备份密码加密；系统不保存密码。Markdown 导出为明文，不可恢复。</p>
        </div>
        <div class="data-security-actions">
          <v-btn color="primary" class="admin-toolbar-btn" prepend-icon="mdi-shield-lock-outline" @click="createOpen = true">
            创建加密备份
          </v-btn>
          <v-btn variant="outlined" color="primary" class="admin-toolbar-btn" prepend-icon="mdi-backup-restore" @click="restoreOpen = true">
            从备份恢复
          </v-btn>
        </div>
      </div>
    </section>

    <section class="data-security-section">
      <div class="data-security-section__head">
        <h2>回收站</h2>
        <p class="data-security-section__hint">
          保留 {{ summary?.trash.retentionDays ?? 30 }} 天，过期后自动永久删除。
        </p>
      </div>

      <div class="admin-filter-row data-security-filter-row">
        <v-text-field
          v-model="keyword"
          class="admin-filter-field"
          density="compact"
          hide-details
          placeholder="搜索名称、平台或渠道"
          prepend-inner-icon="mdi-magnify"
          clearable
        />
        <template v-if="!xs">
          <v-select
            v-model="typeFilter"
            class="admin-filter-select"
            density="compact"
            hide-details
            :items="typeOptions"
            item-title="title"
            item-value="value"
          />
          <div class="admin-filter-actions admin-filter-actions--tools">
            <v-btn class="admin-toolbar-btn" variant="tonal" color="primary" @click="loadTrash(true)">刷新</v-btn>
            <v-btn class="admin-toolbar-btn" color="error" variant="outlined" @click="askPurgeAll">清空回收站</v-btn>
          </div>
        </template>
        <template v-else>
          <div class="admin-filter-actions admin-filter-actions--tools">
            <v-btn
              class="admin-toolbar-btn admin-filter-trigger"
              variant="outlined"
              color="primary"
              prepend-icon="mdi-filter-variant"
              @click="filtersOpen = true"
            >
              筛选
              <span v-if="activeFilterCount" class="admin-filter-trigger__badge">{{ activeFilterCount }}</span>
            </v-btn>
            <v-btn
              class="admin-toolbar-btn"
              variant="outlined"
              color="primary"
              icon="mdi-refresh"
              aria-label="刷新"
              @click="loadTrash(true)"
            />
          </div>
          <div class="admin-filter-actions admin-filter-actions--primary">
            <v-btn class="admin-toolbar-btn" color="error" variant="outlined" @click="askPurgeAll">
              清空回收站
            </v-btn>
          </div>
        </template>
      </div>

      <div ref="scrollEl" class="trash-list">
        <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" />
        <article v-for="asset in items" :key="`${asset.type}-${asset.id}`" class="trash-card">
          <div class="trash-card__body">
            <strong class="trash-card__title">{{ asset.name }}</strong>
            <p class="trash-card__meta">
              {{ asset.type === 'ITEM' ? '账密记录' : '私人模板' }}
              <span v-if="asset.summary"> · {{ asset.summary }}</span>
            </p>
            <p class="trash-card__meta">
              删除于 {{ formatTime(asset.deletedAt) }} · 剩余 {{ asset.remainingDays }} 天
            </p>
          </div>
          <div class="trash-card__actions admin-row-actions">
            <v-btn class="admin-row-actions__btn" variant="flat" color="primary" @click="askRestore(asset)">恢复</v-btn>
            <v-btn class="admin-row-actions__btn" variant="text" color="error" @click="askPurgeOne(asset)">永久删除</v-btn>
          </div>
        </article>
        <p v-if="!loading && items.length === 0" class="trash-list__empty">
          回收站是空的
        </p>
        <p v-if="xs && items.length" class="admin-page__infinite-status">
          <span v-if="loadingMore">已加载 {{ items.length }} / {{ totalElements }} · 加载中…</span>
          <span v-else-if="!hasMore">共 {{ totalElements }} 条 · 已全部加载</span>
          <span v-else>已加载 {{ items.length }} / {{ totalElements }} · 上滑加载更多</span>
        </p>
      </div>
    </section>

    <AdminFilterSheet v-model="filtersOpen" @apply="loadTrash(true)" @reset="typeFilter = 'ALL'">
      <v-select
        v-model="typeFilter"
        hide-details
        :items="typeOptions"
        item-title="title"
        item-value="value"
        label="类型"
      />
    </AdminFilterSheet>

    <BackupCreateDialog v-model="createOpen" @done="refreshAll" />
    <BackupRestoreDialog v-model="restoreOpen" @done="refreshAll" />

    <OsConfirmDialog
      v-model="restoreOneOpen"
      title="恢复该资产？"
      message="将从回收站恢复到保险箱，保留原有 ID 与内容。"
      confirm-text="恢复"
      variant="primary"
      :loading="acting"
      @confirm="confirmRestoreOne"
    />
    <OsConfirmDialog
      v-model="purgeOneOpen"
      title="永久删除？"
      message="删除后无法恢复。请确认这是你要销毁的数据。"
      confirm-text="永久删除"
      variant="danger"
      :loading="acting"
      @confirm="confirmPurgeOne"
    />
    <OsConfirmDialog
      v-model="purgeAllOpen"
      title="清空回收站？"
      message="将永久删除回收站中的全部资产，此操作不可撤销。"
      confirm-text="清空"
      variant="danger"
      :loading="acting"
      @confirm="confirmPurgeAll"
    />

    <v-dialog v-model="reauthOpen" :fullscreen="xs" max-width="420" persistent>
      <v-card class="os-form-dialog">
        <v-card-title>再次验证登录密码</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="reauthPassword"
            type="password"
            label="当前登录密码"
            density="compact"
            hide-details="auto"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="reauthOpen = false">取消</v-btn>
          <v-btn color="primary" :loading="acting" @click="confirmReauth">继续</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </section>
</template>

<style scoped>
.vault-data-security {
  --ds-gap: 10px;
}

.data-security-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: var(--ds-gap);
}

.data-security-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 0;
  padding: 8px 12px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: var(--os-surface);
}

.data-security-card__main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.data-security-card__label {
  color: var(--os-text-muted);
  font-size: 0.6875rem;
  font-weight: 600;
  line-height: 1.2;
}

.data-security-card__value {
  color: var(--os-text-title);
  font-size: 0.875rem;
  font-weight: 650;
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.data-security-card__meta {
  color: var(--os-text-muted);
  font-size: 0.6875rem;
  line-height: 1.3;
}

.data-security-card__action {
  flex: 0 0 auto;
  min-width: 0 !important;
  height: 26px !important;
  min-height: 26px !important;
  padding-inline: 6px !important;
  font-size: 0.75rem !important;
  letter-spacing: 0;
}

.data-security-section {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: var(--os-surface);
}

.data-security-section__head {
  margin-bottom: 8px;
}

.data-security-section__head--row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px 12px;
  margin-bottom: 0;
}

.data-security-section__meta {
  min-width: 0;
  flex: 1 1 220px;
}

.data-security-section__head h2 {
  margin: 0 0 2px;
  color: var(--os-text-title);
  font-size: 0.9375rem;
  font-weight: 650;
  line-height: 1.25;
}

.data-security-section__hint {
  margin: 0;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.35;
}

.data-security-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 0 0 auto;
}

.data-security-filter-row {
  margin-bottom: 8px;
}

.data-security-filter-row :deep(.admin-toolbar-btn) {
  --v-btn-height: var(--os-control-md);
  min-height: var(--os-control-md) !important;
  height: var(--os-control-md) !important;
}

.trash-list {
  display: grid;
  gap: 6px;
  max-height: min(56vh, 520px);
  overflow: auto;
}

.trash-list__empty {
  margin: 0;
  padding: 20px 8px;
  color: var(--os-text-muted);
  font-size: 0.8125rem;
  text-align: center;
}

.trash-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--os-surface) 92%, var(--os-bg));
}

.trash-card__body {
  min-width: 0;
}

.trash-card__title {
  display: block;
  color: var(--os-text-title);
  font-size: 0.875rem;
  font-weight: 650;
  line-height: 1.3;
}

.trash-card__meta {
  margin: 2px 0 0;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.35;
}

.trash-card__actions {
  flex: 0 0 auto;
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 4px;
  width: auto;
}

@media (max-width: 899px) {
  .data-security-section__head--row {
    flex-direction: column;
  }

  .data-security-actions {
    width: 100%;
  }

  .data-security-actions .admin-toolbar-btn {
    flex: 1 1 0;
  }
}

@media (max-width: 599px) {
  .data-security-summary {
    grid-template-columns: 1fr;
  }

  .trash-card {
    align-items: stretch;
    flex-direction: column;
  }

  .trash-card__actions {
    justify-content: flex-end;
    width: 100%;
  }
}
</style>
