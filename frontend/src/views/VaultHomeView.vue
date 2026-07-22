<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDisplay } from 'vuetify'
import { useRouter } from 'vue-router'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import {
  channelExternalHref,
  cloneVaultItemPayload,
  effectiveStatus,
  maskSecret,
  pickListCredentials,
  statusFilterOptions,
  statusLabel,
  truncatePlain,
} from '@/domain/vaultPayload'
import { useVaultStore } from '@/stores/vault'

type 排序方式 = '最近更新' | '最早创建' | '名称'
type 展示方式 = '卡片' | '列表'

const router = useRouter()
const { xs } = useDisplay()
const vault = useVaultStore()
const editor = useVaultItemEditor()

const loading = ref(false)
const errorMessage = ref('')
const mobileFiltersOpen = ref(false)
const searchKeyword = ref('')
const selectedPlatform = ref('全部平台')
const selectedChannel = ref('全部渠道')
const selectedStatus = ref<(typeof statusFilterOptions)[number]>('正常')
const sortBy = ref<排序方式>('最近更新')
const displayMode = ref<展示方式>('卡片')
const snackbar = ref(false)
const snackbarText = ref('')
const abnormalConfirmOpen = ref(false)
const abnormalTargetId = ref<string | null>(null)
const markingAbnormal = ref(false)

const records = computed(() => vault.items.map(({ envelope, payload }) => {
  const { account, password } = pickListCredentials(payload.fields)
  const accountValue = account?.value?.trim() ?? ''
  const passwordValue = password?.value ?? ''
  const status = effectiveStatus(payload)
  return {
    id: envelope.id,
    名称: payload.name,
    平台: payload.platform,
    渠道: payload.channel?.trim() || '',
    渠道筛选: payload.channel?.trim() || '未填写',
    渠道链接: channelExternalHref(payload),
    渠道网址: payload.channelUrl?.trim() || '',
    状态: statusLabel[status],
    状态码: status,
    有效期: payload.expiresAt ? payload.expiresAt.slice(0, 10) : '',
    更新时间: envelope.updatedAt ? new Date(envelope.updatedAt).toLocaleString('zh-CN') : '',
    更新时间戳: envelope.updatedAt ? Date.parse(envelope.updatedAt) : 0,
    账号名: accountValue,
    账号展示: accountValue ? truncatePlain(accountValue) : '',
    账号标签: account?.name || '账号',
    账号可复制: Boolean(accountValue && account?.copyable),
    密码展示: passwordValue ? maskSecret(passwordValue) : '',
    密码标签: password?.name || '密码',
    密码可复制: Boolean(passwordValue && password?.copyable),
    有凭证: Boolean(accountValue || passwordValue),
  }
}))

const platformOptions = computed(() => ['全部平台', ...new Set(records.value.map((item) => item.平台))])
const channelOptions = computed(() => ['全部渠道', ...new Set(records.value.map((item) => item.渠道筛选))])
const statusOptions = [...statusFilterOptions]
const sortOptions: 排序方式[] = ['最近更新', '最早创建', '名称']

const filteredRecords = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const result = records.value.filter((item) => {
    const matchesKeyword = !keyword || [item.名称, item.平台, item.渠道, item.渠道网址, item.账号名, item.有效期]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
    const matchesPlatform = selectedPlatform.value === '全部平台' || item.平台 === selectedPlatform.value
    const matchesChannel = selectedChannel.value === '全部渠道' || item.渠道筛选 === selectedChannel.value
    const matchesStatus = selectedStatus.value === '全部状态' || item.状态 === selectedStatus.value
    return matchesKeyword && matchesPlatform && matchesChannel && matchesStatus
  })

  return [...result].sort((left, right) => {
    if (sortBy.value === '最早创建') return left.更新时间戳 - right.更新时间戳
    if (sortBy.value === '名称') return left.名称.localeCompare(right.名称, 'zh-CN')
    return right.更新时间戳 - left.更新时间戳
  })
})

const hasActiveFilters = computed(() => (
  Boolean(searchKeyword.value.trim())
  || selectedPlatform.value !== '全部平台'
  || selectedChannel.value !== '全部渠道'
  || selectedStatus.value !== '正常'
))

const activeFilterCount = computed(() => [
  selectedPlatform.value !== '全部平台',
  selectedChannel.value !== '全部渠道',
  selectedStatus.value !== '正常',
].filter(Boolean).length)

function resetFilters() {
  searchKeyword.value = ''
  selectedPlatform.value = '全部平台'
  selectedChannel.value = '全部渠道'
  selectedStatus.value = '正常'
}

function openRecord(id: string) {
  router.push(`/vault/items/${id}`)
}

function openEdit(id: string) {
  editor.openEdit(id)
}

function statusTone(status: string) {
  if (status === '正常') return 'ok'
  if (status === '异常') return 'abnormal'
  if (status === '过期') return 'expired'
  return 'archived'
}

function askMarkAbnormal(id: string) {
  abnormalTargetId.value = id
  abnormalConfirmOpen.value = true
}

async function confirmMarkAbnormal() {
  const id = abnormalTargetId.value
  if (!id) return
  markingAbnormal.value = true
  try {
    const item = await vault.getItem(id)
    const payload = cloneVaultItemPayload(item.payload)
    payload.status = 'ABNORMAL'
    await vault.saveItem(id, payload)
    snackbarText.value = '已标记为异常'
    snackbar.value = true
    abnormalConfirmOpen.value = false
    abnormalTargetId.value = null
  } catch (error) {
    snackbarText.value = error instanceof Error && /[\u4e00-\u9fff]/.test(error.message)
      ? error.message
      : '标记异常失败，请重试'
    snackbar.value = true
  } finally {
    markingAbnormal.value = false
  }
}

async function copyText(value: string, label: string, allowed = true) {
  if (!allowed) {
    snackbarText.value = `${label}已设置为不可复制`
    snackbar.value = true
    return
  }
  if (!value) {
    snackbarText.value = `没有可复制的${label}`
    snackbar.value = true
    return
  }
  await navigator.clipboard.writeText(value)
  snackbarText.value = `已复制${label}`
  snackbar.value = true
}

async function copyRecordPassword(id: string, label: string) {
  const source = vault.items.find((item) => item.envelope.id === id)
  const passwordField = source ? pickListCredentials(source.payload.fields).password : null
  await copyText(passwordField?.value ?? '', label, Boolean(passwordField?.copyable))
}

async function loadRecords() {
  loading.value = true
  errorMessage.value = ''
  try {
    await vault.loadItems()
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    errorMessage.value = message && /[\u4e00-\u9fff]/.test(message)
      ? message
      : '保险箱记录加载失败，请检查网络后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <section class="vault-content" aria-labelledby="vault-title">
    <div class="vault-title-row">
      <div>
        <p class="vault-eyebrow">你的私人空间</p>
        <div class="vault-title-row__heading">
          <h1 id="vault-title">我的保险箱</h1>
          <span>{{ filteredRecords.length }} 条记录</span>
        </div>
      </div>
      <v-btn
        color="primary"
        class="vault-new-record"
        size="small"
        prepend-icon="mdi-plus"
        @click="editor.openCreate()"
      >
        新增记录
      </v-btn>
    </div>

    <div class="vault-privacy-note" role="note">
      <v-icon icon="mdi-lock-check-outline" size="16" />
      <span>列表显示账号名与密码暗文；左下图标可标记异常或编辑，点击卡片进入详情。</span>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <div class="vault-controls">
      <v-text-field
        v-model="searchKeyword"
        class="vault-search"
        label="搜索记录"
        placeholder="搜索记录名称、平台、账号或渠道"
        prepend-inner-icon="mdi-magnify"
        hide-details
        clearable
        density="compact"
      />
      <v-btn
        v-if="xs"
        class="vault-mobile-filter-trigger"
        variant="outlined"
        color="primary"
        prepend-icon="mdi-filter-variant"
        @click="mobileFiltersOpen = true"
      >
        筛选与排序<span v-if="activeFilterCount">（{{ activeFilterCount }}）</span>
      </v-btn>
      <div v-if="!xs" class="vault-filters" aria-label="记录筛选">
        <v-select v-model="selectedPlatform" :items="platformOptions" label="平台" hide-details density="compact" />
        <v-select v-model="selectedChannel" :items="channelOptions" label="渠道" hide-details density="compact" />
        <v-select v-model="selectedStatus" :items="statusOptions" label="状态" hide-details density="compact" />
        <v-btn v-if="hasActiveFilters" variant="text" color="primary" size="small" @click="resetFilters">清除筛选</v-btn>
      </div>
      <div v-if="!xs" class="vault-view-controls">
        <v-select v-model="sortBy" :items="sortOptions" label="排序" hide-details density="compact" />
        <div class="vault-view-switch" role="group" aria-label="展示方式">
          <v-btn
            :variant="displayMode === '卡片' ? 'tonal' : 'text'"
            icon="mdi-view-grid-outline"
            size="small"
            aria-label="卡片视图"
            @click="displayMode = '卡片'"
          />
          <v-btn
            :variant="displayMode === '列表' ? 'tonal' : 'text'"
            icon="mdi-format-list-bulleted"
            size="small"
            aria-label="列表视图"
            @click="displayMode = '列表'"
          />
        </div>
      </div>
    </div>

    <v-dialog
      v-model="mobileFiltersOpen"
      fullscreen
      transition="dialog-bottom-transition"
    >
      <v-card class="vault-mobile-filters">
        <header class="vault-mobile-filters__header">
          <div>
            <p class="vault-eyebrow">快速缩小范围</p>
            <h2>筛选与排序</h2>
          </div>
          <v-btn icon="mdi-close" variant="text" aria-label="关闭筛选" @click="mobileFiltersOpen = false" />
        </header>
        <div class="vault-mobile-filters__body">
          <v-select v-model="selectedPlatform" :items="platformOptions" label="平台" hide-details />
          <v-select v-model="selectedChannel" :items="channelOptions" label="渠道" hide-details />
          <v-select v-model="selectedStatus" :items="statusOptions" label="状态" hide-details />
          <v-select v-model="sortBy" :items="sortOptions" label="排序" hide-details />
          <div class="vault-mobile-filters__mode">
            <span>展示方式</span>
            <v-btn-toggle v-model="displayMode" mandatory color="primary" density="comfortable">
              <v-btn value="卡片" prepend-icon="mdi-view-grid-outline">卡片</v-btn>
              <v-btn value="列表" prepend-icon="mdi-format-list-bulleted">列表</v-btn>
            </v-btn-toggle>
          </div>
        </div>
        <div class="vault-mobile-filters__actions">
          <v-btn variant="text" color="primary" :disabled="!hasActiveFilters" @click="resetFilters">清除条件</v-btn>
          <v-btn color="primary" @click="mobileFiltersOpen = false">查看 {{ filteredRecords.length }} 条记录</v-btn>
        </div>
      </v-card>
    </v-dialog>

    <div
      v-if="!loading && !errorMessage && filteredRecords.length"
      class="vault-records"
      :class="{ 'vault-records--list': displayMode === '列表' }"
    >
      <article
        v-for="item in filteredRecords"
        :key="item.id"
        class="vault-record-card"
        role="button"
        tabindex="0"
        :aria-label="`查看记录 ${item.名称}`"
        @click="openRecord(item.id)"
        @keydown.enter.prevent="openRecord(item.id)"
        @keydown.space.prevent="openRecord(item.id)"
      >
        <div class="vault-record-card__heading">
          <v-avatar size="32" rounded="lg" class="vault-record-card__icon">
            <v-icon icon="mdi-key-variant" color="primary" size="16" />
          </v-avatar>
          <div class="vault-record-card__title">
            <strong>{{ item.名称 }}</strong>
            <span class="vault-record-card__platform">{{ item.平台 }}</span>
          </div>
          <span class="vault-record-card__status" :data-tone="statusTone(item.状态)">{{ item.状态 }}</span>
        </div>

        <div
          v-if="item.渠道 || item.渠道链接"
          class="vault-record-card__source"
          @click.stop
          @keydown.stop
        >
          <span class="vault-record-card__source-label">来源</span>
          <a
            v-if="item.渠道链接"
            class="vault-record-card__source-value"
            :href="item.渠道链接"
            target="_blank"
            rel="noopener noreferrer"
            :title="item.渠道 || item.渠道网址"
            @click.stop
          >{{ item.渠道 || item.渠道网址 }}</a>
          <span
            v-else
            class="vault-record-card__source-value"
            :title="item.渠道"
          >{{ item.渠道 }}</span>
        </div>

        <div class="vault-record-card__secrets" @click.stop @keydown.stop>
          <template v-if="item.有凭证">
            <div class="vault-secret-row">
              <span class="vault-secret-row__label">账号</span>
              <span class="vault-secret-row__value" :title="item.账号名 || undefined">
                {{ item.账号展示 || '—' }}
              </span>
              <button
                v-if="item.账号可复制"
                type="button"
                class="vault-secret-row__copy"
                :aria-label="`复制${item.账号标签}`"
                @click="copyText(item.账号名, item.账号标签, item.账号可复制)"
              >
                <v-icon icon="mdi-content-copy" size="13" />
              </button>
            </div>
            <div class="vault-secret-row">
              <span class="vault-secret-row__label">密码</span>
              <span class="vault-secret-row__value vault-secret-row__value--masked">
                {{ item.密码展示 || '—' }}
              </span>
              <button
                v-if="item.密码可复制"
                type="button"
                class="vault-secret-row__copy"
                :aria-label="`复制${item.密码标签}`"
                @click="copyRecordPassword(item.id, item.密码标签)"
              >
                <v-icon icon="mdi-content-copy" size="13" />
              </button>
            </div>
          </template>
          <div v-else class="vault-record-card__empty-secrets">无账号或密码字段</div>
        </div>

        <div class="vault-record-card__footer" @click.stop @keydown.stop>
          <div class="vault-record-card__icon-actions">
            <button
              v-if="item.状态码 !== 'ABNORMAL'"
              type="button"
              class="vault-record-card__icon-btn vault-record-card__icon-btn--warn"
              aria-label="标记异常"
              title="标记异常"
              @click="askMarkAbnormal(item.id)"
            >
              <v-icon icon="mdi-alert-outline" size="16" />
            </button>
            <button
              type="button"
              class="vault-record-card__icon-btn vault-record-card__icon-btn--edit"
              aria-label="编辑"
              title="编辑"
              @click="openEdit(item.id)"
            >
              <v-icon icon="mdi-pencil-outline" size="16" />
            </button>
          </div>
          <time v-if="item.有效期">有效期截至 {{ item.有效期 }}</time>
          <span v-else class="vault-record-card__expiry-empty">未设置有效期</span>
        </div>
      </article>
    </div>

    <section v-else-if="!loading && errorMessage" class="vault-empty-state" aria-label="加载失败">
      <div class="vault-empty-state__illustration vault-empty-state__illustration--error" aria-hidden="true">
        <v-icon icon="mdi-cloud-alert-outline" size="36" />
      </div>
      <p class="vault-eyebrow">暂时无法读取记录</p>
      <h2>保险箱加载失败</h2>
      <p>{{ errorMessage }}</p>
      <v-btn color="primary" prepend-icon="mdi-refresh" @click="loadRecords">重新加载</v-btn>
    </section>

    <section v-else-if="!loading && records.length === 0" class="vault-empty-state" aria-label="空保险箱提示">
      <div class="vault-empty-state__illustration" aria-hidden="true">
        <v-icon icon="mdi-safe-square-outline" size="36" />
      </div>
      <p class="vault-eyebrow">从第一条记录开始</p>
      <h2>你的保险箱还是空的</h2>
      <p>新增后可按平台、渠道、状态在本地搜索；可复制账号密码，也可直接编辑或标记异常。</p>
      <v-btn color="primary" prepend-icon="mdi-plus" @click="editor.openCreate()">
        添加第一条记录
      </v-btn>
    </section>

    <section v-else-if="!loading" class="vault-empty-state" aria-label="无匹配记录">
      <div class="vault-empty-state__illustration" aria-hidden="true">
        <v-icon icon="mdi-filter-off-outline" size="36" />
      </div>
      <p class="vault-eyebrow">没有匹配结果</p>
      <h2>换一组筛选条件试试</h2>
      <p>当前保险箱中已有 {{ records.length }} 条记录，但没有记录符合本次搜索与筛选条件。</p>
      <v-btn variant="outlined" color="primary" prepend-icon="mdi-filter-remove-outline" @click="resetFilters">
        清除筛选
      </v-btn>
    </section>

    <v-dialog v-model="abnormalConfirmOpen" max-width="360">
      <v-card class="vault-confirm-dialog">
        <v-card-title class="vault-confirm-dialog__title">确认标记异常？</v-card-title>
        <v-card-text class="vault-confirm-dialog__body">
          标记后该记录状态将变为「异常」。此操作可在列表中立即看到，是否继续？
        </v-card-text>
        <v-card-actions class="vault-confirm-dialog__actions">
          <v-spacer />
          <v-btn
            variant="text"
            size="small"
            density="compact"
            :disabled="markingAbnormal"
            @click="abnormalConfirmOpen = false"
          >
            取消
          </v-btn>
          <v-btn
            color="error"
            variant="text"
            size="small"
            density="compact"
            :loading="markingAbnormal"
            @click="confirmMarkAbnormal"
          >
            确认标记
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar" class="vault-feedback-snackbar" location="bottom" :timeout="2000">{{ snackbarText }}</v-snackbar>
  </section>
</template>
