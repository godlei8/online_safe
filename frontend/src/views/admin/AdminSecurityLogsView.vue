<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { ApiRequestError } from '@/api/client'
import {
  securityLogsApi,
  type SecurityLogActorType,
  type SecurityLogCategory,
  type SecurityLogDetail,
  type SecurityLogListItem,
  type SecurityLogResult,
  type SecurityLogRiskLevel,
  type SecurityLogStats,
} from '@/api/securityLogs'
import AdminEllipsisText from '@/components/AdminEllipsisText.vue'

type TimePreset = '24h' | '7d' | '30d' | 'custom'

const { smAndDown } = useDisplay()

const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const detailError = ref('')
const items = ref<SecurityLogListItem[]>([])
const stats = ref<SecurityLogStats>({
  loginFailuresLast24Hours: 0,
  highRiskLast7Days: 0,
  adminActionsLast7Days: 0,
  blockedLast24Hours: 0,
})
const page = ref(1)
const pageSize = 20
const totalElements = ref(0)

const timePreset = ref<TimePreset>('7d')
const customFromLocal = ref('')
const customToLocal = ref('')
const categoryFilter = ref('')
const resultFilter = ref('')
const riskFilter = ref('')
const actorTypeFilter = ref('')
const query = ref('')

const detailOpen = ref(false)
const selectedId = ref<string | null>(null)
const detail = ref<SecurityLogDetail | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize)))

const hasActiveFilters = computed(() => Boolean(
  categoryFilter.value ||
  resultFilter.value ||
  riskFilter.value ||
  actorTypeFilter.value ||
  query.value.trim(),
))

const emptyMessage = computed(() => (
  hasActiveFilters.value
    ? '当前筛选条件下没有匹配的安全日志。'
    : '所选时间范围内暂无安全日志。'
))

const timeOptions = [
  { title: '最近 24 小时', value: '24h' },
  { title: '最近 7 天', value: '7d' },
  { title: '最近 30 天', value: '30d' },
  { title: '自定义', value: 'custom' },
]

const categoryOptions = [
  { title: '分类：全部', value: '' },
  { title: '认证', value: 'AUTH' },
  { title: '账户', value: 'ACCOUNT' },
  { title: '会话', value: 'SESSION' },
  { title: '邀请码', value: 'INVITATION' },
  { title: '公告', value: 'ANNOUNCEMENT' },
  { title: '模板', value: 'TEMPLATE' },
  { title: '设置', value: 'SETTINGS' },
  { title: '系统', value: 'SYSTEM' },
]

const resultOptions = [
  { title: '结果：全部', value: '' },
  { title: '成功', value: 'SUCCESS' },
  { title: '失败', value: 'FAILED' },
  { title: '已拦截', value: 'BLOCKED' },
]

const riskOptions = [
  { title: '风险：全部', value: '' },
  { title: '普通', value: 'INFO' },
  { title: '注意', value: 'WARNING' },
  { title: '高风险', value: 'HIGH' },
]

const actorTypeOptions = [
  { title: '主体：全部', value: '' },
  { title: '个人用户', value: 'USER' },
  { title: '管理员', value: 'ADMIN' },
  { title: '匿名', value: 'ANONYMOUS' },
  { title: '系统', value: 'SYSTEM' },
]

const metadataLabels: Record<string, string> = {
  inviteUsed: '使用邀请码',
  purpose: '用途',
  previousStatus: '原状态',
  newStatus: '新状态',
  sessionsRevoked: '失效会话数',
  previousUsernameHint: '原用户名摘要',
  newUsernameHint: '新用户名摘要',
  codeHint: '邀请码提示',
  maxUses: '最大使用次数',
  validDays: '有效天数',
  title: '标题',
  name: '名称',
  status: '状态',
  changedKeys: '变更项',
  previousDays: '原保留天数',
  newDays: '新保留天数',
  deletedCount: '删除条数',
  cutoffAt: '截止时间点',
  errorCode: '错误码',
  windowSeconds: '聚合窗口（秒）',
}

onMounted(() => {
  initCustomRange()
  void loadData()
})

watch([page, categoryFilter, resultFilter, riskFilter, actorTypeFilter], () => {
  void loadData()
})

watch(timePreset, (value) => {
  if (value !== 'custom') {
    page.value = 1
    void loadData()
  }
})

function initCustomRange() {
  const now = new Date()
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
  customFromLocal.value = toLocalInputValue(weekAgo)
  customToLocal.value = toLocalInputValue(now)
}

function toLocalInputValue(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function resolveTimeRange(): { from: string; to: string } | null {
  const now = new Date()
  if (timePreset.value === 'custom') {
    if (!customFromLocal.value || !customToLocal.value) return null
    const from = new Date(customFromLocal.value)
    const to = new Date(customToLocal.value)
    if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) return null
    if (from.getTime() > to.getTime()) return null
    return { from: from.toISOString(), to: to.toISOString() }
  }

  const hoursMap: Record<Exclude<TimePreset, 'custom'>, number> = {
    '24h': 24,
    '7d': 7 * 24,
    '30d': 30 * 24,
  }
  const hours = hoursMap[timePreset.value]
  const from = new Date(now.getTime() - hours * 60 * 60 * 1000)
  return { from: from.toISOString(), to: now.toISOString() }
}

async function loadData() {
  const range = resolveTimeRange()
  if (!range) {
    errorMessage.value = '请填写有效的时间范围。'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const [statsResult, listResult] = await Promise.all([
      securityLogsApi.stats(),
      securityLogsApi.list({
        page: page.value - 1,
        size: pageSize,
        from: range.from,
        to: range.to,
        category: (categoryFilter.value || undefined) as SecurityLogCategory | undefined,
        result: (resultFilter.value || undefined) as SecurityLogResult | undefined,
        riskLevel: (riskFilter.value || undefined) as SecurityLogRiskLevel | undefined,
        actorType: (actorTypeFilter.value || undefined) as SecurityLogActorType | undefined,
        q: query.value.trim() || undefined,
      }),
    ])
    stats.value = statsResult
    items.value = listResult.content
    totalElements.value = listResult.totalElements
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '加载安全日志失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function search() {
  page.value = 1
  await loadData()
}

async function openDetail(id: string) {
  selectedId.value = id
  detailOpen.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await securityLogsApi.detail(id)
  } catch (error) {
    detailError.value = error instanceof ApiRequestError ? error.message : '加载日志详情失败，请稍后重试。'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailOpen.value = false
  selectedId.value = null
  detail.value = null
  detailError.value = ''
}

function formatTime(value: string | null | undefined) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function riskLabel(level: string) {
  switch (level) {
    case 'INFO': return '普通'
    case 'WARNING': return '注意'
    case 'HIGH': return '高风险'
    default: return level
  }
}

function riskTone(level: string) {
  switch (level) {
    case 'INFO': return 'info'
    case 'WARNING': return 'warning'
    case 'HIGH': return 'error'
    default: return 'secondary'
  }
}

function resultLabel(result: string) {
  switch (result) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'BLOCKED': return '已拦截'
    default: return result
  }
}

function resultTone(result: string) {
  switch (result) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'warning'
    case 'BLOCKED': return 'error'
    default: return 'secondary'
  }
}

function categoryLabel(category: string) {
  return categoryOptions.find((item) => item.value === category)?.title.replace(/^分类：/, '') ?? category
}

function actorTypeLabel(actorType: string) {
  return actorTypeOptions.find((item) => item.value === actorType)?.title.replace(/^主体：/, '') ?? actorType
}

function deviceTypeLabel(deviceType: string | null | undefined) {
  switch (deviceType) {
    case 'DESKTOP': return '桌面'
    case 'MOBILE': return '手机'
    case 'TABLET': return '平板'
    case 'UNKNOWN': return '未知'
    default: return deviceType || '—'
  }
}

function formatMetadataValue(key: string, value: unknown) {
  if (value == null) return '—'
  if (key === 'cutoffAt' && typeof value === 'string') return formatTime(value)
  if (Array.isArray(value)) return value.map(String).join('、')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

const detailFields = computed(() => {
  if (!detail.value) return []
  const item = detail.value
  return [
    { label: '事件编号', value: item.id },
    { label: '发生时间', value: formatTime(item.occurredAt) },
    { label: '分类', value: categoryLabel(item.category) },
    { label: '事件', value: item.eventLabelZh },
    { label: '事件编码', value: item.eventType },
    { label: '风险等级', value: riskLabel(item.riskLevel), tone: riskTone(item.riskLevel) },
    { label: '结果', value: resultLabel(item.result), tone: resultTone(item.result) },
    { label: '操作主体', value: item.actorLabel },
    { label: '主体类型', value: actorTypeLabel(item.actorType) },
    { label: '主体 ID', value: item.actorId || '—' },
    { label: '操作目标', value: item.targetLabel },
    { label: '目标类型', value: item.targetType || '—' },
    { label: '目标 ID', value: item.targetId || '—' },
    { label: '错误码', value: item.errorCode || '—' },
    { label: '请求 ID', value: item.requestId || '—' },
    { label: '路由', value: item.routeTemplate || '—' },
    { label: '请求方法', value: item.httpMethod || '—' },
    { label: '来源 IP', value: item.ipMasked || '—' },
    { label: '浏览器', value: item.browserFamily || '—' },
    { label: '操作系统', value: item.osFamily || '—' },
    { label: '设备类型', value: deviceTypeLabel(item.deviceType) },
    { label: '聚合次数', value: String(item.occurrenceCount) },
  ]
})

const metadataEntries = computed(() => {
  if (!detail.value?.metadata) return []
  return Object.entries(detail.value.metadata).map(([key, value]) => ({
    key,
    label: metadataLabels[key] ?? key,
    value: formatMetadataValue(key, value),
  }))
})
</script>

<template>
  <div class="admin-security-logs">
    <div class="d-flex justify-end mb-4">
      <v-btn
        class="admin-toolbar-btn"
        color="primary"
        prepend-icon="mdi-refresh"
        :loading="loading"
        @click="loadData"
      >
        刷新
      </v-btn>
    </div>

    <div class="admin-stat-grid mb-4">
      <v-card class="admin-stat-card admin-stat-card--warning" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">近 24 小时登录失败</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-shield-alert-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.loginFailuresLast24Hours }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--error" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">近 7 天高风险事件</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-alert-decagram-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.highRiskLast7Days }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--primary" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">近 7 天管理员操作</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-account-cog-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.adminActionsLast7Days }}</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--info" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">近 24 小时被拦截</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-block-helper" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ stats.blockedLast24Hours }}</div>
      </v-card>
    </div>

    <v-card class="admin-panel mb-4" elevation="0">
      <div class="admin-filter-row">
        <v-select
          v-model="timePreset"
          class="admin-filter-select"
          hide-details
          :items="timeOptions"
          item-title="title"
          item-value="value"
        />
        <v-select
          v-model="categoryFilter"
          class="admin-filter-select"
          hide-details
          :items="categoryOptions"
          item-title="title"
          item-value="value"
        />
        <v-select
          v-model="resultFilter"
          class="admin-filter-select"
          hide-details
          :items="resultOptions"
          item-title="title"
          item-value="value"
        />
        <v-select
          v-model="riskFilter"
          class="admin-filter-select"
          hide-details
          :items="riskOptions"
          item-title="title"
          item-value="value"
        />
        <v-select
          v-model="actorTypeFilter"
          class="admin-filter-select"
          hide-details
          :items="actorTypeOptions"
          item-title="title"
          item-value="value"
        />
        <v-text-field
          v-model="query"
          class="admin-filter-field"
          hide-details
          label="搜索"
          placeholder="事件编号、用户名或目标标识"
          prepend-inner-icon="mdi-magnify"
          @keyup.enter="search"
        />
        <template v-if="timePreset === 'custom'">
          <v-text-field
            v-model="customFromLocal"
            class="admin-filter-field"
            hide-details
            label="开始时间"
            type="datetime-local"
          />
          <v-text-field
            v-model="customToLocal"
            class="admin-filter-field"
            hide-details
            label="结束时间"
            type="datetime-local"
          />
        </template>
        <v-btn class="admin-toolbar-btn" variant="tonal" color="primary" @click="search">查询</v-btn>
      </div>
    </v-card>

    <v-alert
      v-if="errorMessage"
      type="error"
      variant="tonal"
      class="mb-4"
    >
      <div class="d-flex flex-wrap align-center justify-space-between gap-3">
        <span>{{ errorMessage }}</span>
        <v-btn class="admin-toolbar-btn" size="small" variant="text" color="error" @click="loadData">重新加载</v-btn>
      </div>
    </v-alert>

    <v-card class="admin-panel" elevation="0">
      <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" />

      <div v-if="smAndDown" class="admin-security-log-cards">
        <div v-if="!loading && items.length === 0" class="admin-security-log-empty">
          <v-icon icon="mdi-shield-search" size="28" color="primary" />
          <strong>{{ hasActiveFilters ? '筛选无结果' : '暂无日志' }}</strong>
          <p>{{ emptyMessage }}</p>
        </div>

        <v-card
          v-for="item in items"
          :key="item.id"
          class="admin-security-log-card"
          elevation="0"
          @click="openDetail(item.id)"
        >
          <div class="admin-security-log-card__head">
            <time>{{ formatTime(item.occurredAt) }}</time>
            <span class="admin-status-text" :data-tone="riskTone(item.riskLevel)">
              {{ riskLabel(item.riskLevel) }}
            </span>
          </div>
          <div class="admin-security-log-card__title">
            {{ item.eventLabelZh }}
            <span v-if="item.occurrenceCount > 1" class="admin-security-log-card__badge">
              ×{{ item.occurrenceCount }}
            </span>
          </div>
          <div class="admin-security-log-card__meta">
            <span>主体：{{ item.actorLabel }}</span>
            <span>目标：{{ item.targetLabel }}</span>
          </div>
          <div class="admin-security-log-card__foot">
            <span class="admin-status-text" :data-tone="resultTone(item.result)">
              {{ resultLabel(item.result) }}
            </span>
            <span class="text-caption text-medium-emphasis">{{ item.sourceSummary }}</span>
          </div>
        </v-card>
      </div>

      <v-table v-else class="admin-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>风险</th>
            <th>事件</th>
            <th>操作主体</th>
            <th>操作目标</th>
            <th>结果</th>
            <th>来源摘要</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && items.length === 0">
            <td colspan="8" class="admin-security-log-empty admin-security-log-empty--table">
              <v-icon icon="mdi-shield-search" size="28" color="primary" />
              <strong>{{ hasActiveFilters ? '筛选无结果' : '暂无日志' }}</strong>
              <p>{{ emptyMessage }}</p>
            </td>
          </tr>
          <tr v-for="item in items" :key="item.id">
            <td data-label="时间">
              <AdminEllipsisText :text="formatTime(item.occurredAt)" max-width="9rem" />
            </td>
            <td data-label="风险">
              <span class="admin-status-text" :data-tone="riskTone(item.riskLevel)">
                {{ riskLabel(item.riskLevel) }}
              </span>
            </td>
            <td data-label="事件">
              <div class="admin-table__stack">
                <AdminEllipsisText class="font-weight-medium" :text="item.eventLabelZh" max-width="12rem" />
                <span v-if="item.occurrenceCount > 1" class="text-caption text-medium-emphasis">
                  聚合 {{ item.occurrenceCount }} 次
                </span>
              </div>
            </td>
            <td data-label="操作主体">
              <AdminEllipsisText :text="item.actorLabel" max-width="10rem" />
            </td>
            <td data-label="操作目标">
              <AdminEllipsisText :text="item.targetLabel" max-width="10rem" />
            </td>
            <td data-label="结果">
              <span class="admin-status-text" :data-tone="resultTone(item.result)">
                {{ resultLabel(item.result) }}
              </span>
            </td>
            <td data-label="来源摘要">
              <AdminEllipsisText :text="item.sourceSummary" max-width="11rem" />
            </td>
            <td data-label="详情">
              <v-btn
                class="admin-row-actions__btn"
                size="x-small"
                variant="text"
                color="primary"
                prepend-icon="mdi-text-box-search-outline"
                @click="openDetail(item.id)"
              >
                详情
              </v-btn>
            </td>
          </tr>
        </tbody>
      </v-table>

      <div class="admin-pagination-row mt-4">
        <div class="text-caption text-medium-emphasis">
          共 {{ totalElements }} 条，第 {{ page }} / {{ totalPages }} 页
        </div>
        <v-pagination v-model="page" :length="totalPages" total-visible="5" />
      </div>
    </v-card>

    <v-navigation-drawer
      v-model="detailOpen"
      class="admin-security-log-drawer"
      :class="{ 'admin-security-log-drawer--mobile': smAndDown }"
      location="end"
      temporary
      :width="smAndDown ? '100%' : 420"
      color="surface"
      @update:model-value="(open) => { if (!open) closeDetail() }"
    >
      <header class="admin-security-log-drawer__header">
        <div>
          <strong>日志详情</strong>
          <p v-if="selectedId" class="text-caption text-medium-emphasis">{{ selectedId }}</p>
        </div>
        <v-btn icon="mdi-close" variant="text" aria-label="关闭详情" @click="closeDetail" />
      </header>

      <v-progress-linear v-if="detailLoading" indeterminate color="primary" />

      <v-alert v-else-if="detailError" type="error" variant="tonal" class="ma-4">
        <div class="d-flex flex-wrap align-center justify-space-between gap-3">
          <span>{{ detailError }}</span>
          <v-btn
            v-if="selectedId"
            class="admin-toolbar-btn"
            size="small"
            variant="text"
            color="error"
            @click="openDetail(selectedId)"
          >
            重试
          </v-btn>
        </div>
      </v-alert>

      <div v-else-if="detail" class="admin-security-log-drawer__body">
        <section class="admin-security-log-detail-section">
          <h3 class="admin-panel__title">基本信息</h3>
          <dl class="admin-security-log-detail-list">
            <div v-for="field in detailFields" :key="field.label" class="admin-security-log-detail-list__row">
              <dt>{{ field.label }}</dt>
              <dd>
                <span
                  v-if="field.tone"
                  class="admin-status-text"
                  :data-tone="field.tone"
                >
                  {{ field.value }}
                </span>
                <template v-else>{{ field.value }}</template>
              </dd>
            </div>
          </dl>
        </section>

        <section v-if="metadataEntries.length > 0" class="admin-security-log-detail-section">
          <h3 class="admin-panel__title">事件元数据</h3>
          <dl class="admin-security-log-detail-list">
            <div
              v-for="entry in metadataEntries"
              :key="entry.key"
              class="admin-security-log-detail-list__row"
            >
              <dt>{{ entry.label }}</dt>
              <dd>{{ entry.value }}</dd>
            </div>
          </dl>
        </section>
      </div>
    </v-navigation-drawer>
  </div>
</template>

<style scoped>
.admin-security-log-cards {
  display: grid;
  gap: 12px;
}

.admin-security-log-card {
  border: 1px solid var(--os-border);
  border-radius: var(--os-radius-card);
  padding: 14px;
  cursor: pointer;
  transition: box-shadow 160ms ease, border-color 160ms ease;
}

.admin-security-log-card:hover {
  border-color: color-mix(in srgb, var(--os-primary) 24%, var(--os-border));
  box-shadow: var(--os-shadow-1);
}

.admin-security-log-card__head,
.admin-security-log-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-security-log-card__head time {
  color: var(--os-text-muted);
  font-size: 0.75rem;
}

.admin-security-log-card__title {
  margin-top: 8px;
  color: var(--os-text-title);
  font-weight: 600;
}

.admin-security-log-card__badge {
  margin-left: 6px;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  font-weight: 500;
}

.admin-security-log-card__meta {
  display: grid;
  gap: 4px;
  margin-top: 8px;
  color: var(--os-text-muted);
  font-size: 0.8125rem;
}

.admin-security-log-card__foot {
  margin-top: 10px;
}

.admin-security-log-empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 32px 16px;
  color: var(--os-text-muted);
  text-align: center;
}

.admin-security-log-empty strong {
  color: var(--os-text-title);
}

.admin-security-log-empty p {
  margin: 0;
  max-width: 24rem;
  font-size: 0.875rem;
}

.admin-security-log-empty--table {
  display: grid;
  justify-items: center;
}

.admin-security-log-drawer :deep(.v-navigation-drawer__content) {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
}

.admin-security-log-drawer__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--os-border);
}

.admin-security-log-drawer__header strong {
  color: var(--os-text-title);
  font-size: 1rem;
}

.admin-security-log-drawer__header p {
  margin: 4px 0 0;
  word-break: break-all;
}

.admin-security-log-drawer__body {
  overflow: auto;
  padding: 16px;
}

.admin-security-log-detail-section + .admin-security-log-detail-section {
  margin-top: 20px;
}

.admin-security-log-detail-list {
  display: grid;
  gap: 10px;
  margin: 12px 0 0;
}

.admin-security-log-detail-list__row {
  display: grid;
  grid-template-columns: minmax(88px, 0.38fr) minmax(0, 0.62fr);
  gap: 12px;
  align-items: start;
}

.admin-security-log-detail-list dt {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  font-weight: 600;
}

.admin-security-log-detail-list dd {
  margin: 0;
  color: var(--os-text-title);
  font-size: 0.875rem;
  word-break: break-word;
}

.admin-security-log-drawer--mobile .admin-security-log-detail-list__row {
  grid-template-columns: 1fr;
  gap: 4px;
}

@media (prefers-reduced-motion: reduce) {
  .admin-security-log-card { transition: none; }
}
</style>
