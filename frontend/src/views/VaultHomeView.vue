<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useVaultStore } from '@/stores/vault'

type 排序方式 = '最近更新' | '最早创建' | '名称'
type 展示方式 = '卡片' | '列表'

const router = useRouter()
const vault = useVaultStore()
const editor = useVaultItemEditor()

const loading = ref(false)
const searchKeyword = ref('')
const selectedPlatform = ref('全部平台')
const selectedChannel = ref('全部渠道')
const selectedStatus = ref('全部状态')
const sortBy = ref<排序方式>('最近更新')
const displayMode = ref<展示方式>('卡片')
const snackbar = ref(false)
const snackbarText = ref('')

const records = computed(() => vault.listRows.map((item) => ({
  id: item.id,
  名称: item.name,
  平台: item.platform,
  渠道: item.channel,
  状态: item.status,
  标签: item.tags,
  更新时间: item.updatedAt ? new Date(item.updatedAt).toLocaleString('zh-CN') : '',
  更新时间戳: item.updatedAtMs,
})))

const platformOptions = computed(() => ['全部平台', ...new Set(records.value.map((item) => item.平台))])
const channelOptions = computed(() => ['全部渠道', ...new Set(records.value.map((item) => item.渠道))])
const statusOptions = computed(() => ['全部状态', ...new Set(records.value.map((item) => item.状态))])
const sortOptions: 排序方式[] = ['最近更新', '最早创建', '名称']

const filteredRecords = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const result = records.value.filter((item) => {
    const matchesKeyword = !keyword || [item.名称, item.平台, item.渠道, ...item.标签]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
    const matchesPlatform = selectedPlatform.value === '全部平台' || item.平台 === selectedPlatform.value
    const matchesChannel = selectedChannel.value === '全部渠道' || item.渠道 === selectedChannel.value
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
  || selectedStatus.value !== '全部状态'
))

function resetFilters() {
  searchKeyword.value = ''
  selectedPlatform.value = '全部平台'
  selectedChannel.value = '全部渠道'
  selectedStatus.value = '全部状态'
}

function openRecord(id: string) {
  router.push(`/vault/items/${id}`)
}

function openEdit(id: string) {
  editor.openEdit(id)
}

function statusColor(status: string) {
  if (status === '正常') return 'success'
  if (status === '待验证' || status === '异常') return 'warning'
  return 'secondary'
}

async function copyFirstCopyable(id: string) {
  const item = vault.items.find((entry) => entry.envelope.id === id)
  const field = item?.payload.fields.find((entry) => entry.copyable && entry.value)
  if (!field) {
    snackbarText.value = '没有可复制的字段'
    snackbar.value = true
    return
  }
  await navigator.clipboard.writeText(field.value)
  snackbarText.value = `已复制${field.name}`
  snackbar.value = true
}

onMounted(async () => {
  loading.value = true
  try {
    await vault.loadItems()
  } finally {
    loading.value = false
  }
})
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
        prepend-icon="mdi-plus"
        @click="editor.openCreate()"
      >
        新增记录
      </v-btn>
    </div>

    <div class="vault-privacy-note" role="note">
      <v-icon icon="mdi-lock-check-outline" size="18" />
      <span>首页只展示元数据；打开详情后可完整查看并单字段复制。敏感字段默认明文。</span>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <div class="vault-controls">
      <v-text-field
        v-model="searchKeyword"
        class="vault-search"
        label="搜索记录"
        placeholder="搜索记录名称、平台或标签"
        prepend-inner-icon="mdi-magnify"
        hide-details
        clearable
      />
      <div class="vault-filters" aria-label="记录筛选">
        <v-select v-model="selectedPlatform" :items="platformOptions" label="平台" hide-details />
        <v-select v-model="selectedChannel" :items="channelOptions" label="渠道" hide-details />
        <v-select v-model="selectedStatus" :items="statusOptions" label="状态" hide-details />
        <v-btn v-if="hasActiveFilters" variant="text" color="primary" @click="resetFilters">清除筛选</v-btn>
      </div>
      <div class="vault-view-controls">
        <v-select v-model="sortBy" :items="sortOptions" label="排序" hide-details />
        <div class="vault-view-switch" role="group" aria-label="展示方式">
          <v-btn
            :variant="displayMode === '卡片' ? 'tonal' : 'text'"
            icon="mdi-view-grid-outline"
            aria-label="卡片视图"
            @click="displayMode = '卡片'"
          />
          <v-btn
            :variant="displayMode === '列表' ? 'tonal' : 'text'"
            icon="mdi-format-list-bulleted"
            aria-label="列表视图"
            @click="displayMode = '列表'"
          />
        </div>
      </div>
    </div>

    <div
      v-if="filteredRecords.length"
      class="vault-records"
      :class="{ 'vault-records--list': displayMode === '列表' }"
    >
      <article v-for="item in filteredRecords" :key="item.id" class="vault-record-card">
        <button class="vault-record-card__main" type="button" @click="openRecord(item.id)">
          <div class="vault-record-card__heading">
            <v-avatar size="38" rounded="lg" color="blue-lighten-5">
              <v-icon icon="mdi-key-variant" color="primary" size="19" />
            </v-avatar>
            <div>
              <strong>{{ item.名称 }}</strong>
              <span>{{ item.平台 }} · {{ item.渠道 }}</span>
            </div>
            <v-chip :color="statusColor(item.状态)" size="x-small" variant="tonal">{{ item.状态 }}</v-chip>
          </div>
          <div class="vault-record-card__hidden">
            <v-icon icon="mdi-eye-off-outline" size="16" />
            列表不展示敏感字段值
          </div>
          <div class="vault-record-card__footer">
            <span>{{ item.标签.join(' · ') || '无标签' }}</span>
            <time>{{ item.更新时间 }}</time>
          </div>
        </button>
        <div class="vault-record-card__actions">
          <v-btn size="small" variant="text" prepend-icon="mdi-content-copy" @click="copyFirstCopyable(item.id)">
            复制
          </v-btn>
          <v-btn size="small" variant="text" @click="openEdit(item.id)">编辑</v-btn>
          <v-btn size="small" variant="text" @click="openRecord(item.id)">详情</v-btn>
        </div>
      </article>
    </div>

    <section v-else class="vault-empty-state" aria-label="空保险箱提示">
      <div class="vault-empty-state__illustration" aria-hidden="true">
        <v-icon icon="mdi-safe-square-outline" size="42" />
      </div>
      <p class="vault-eyebrow">从第一条记录开始</p>
      <h2>你的保险箱还是空的</h2>
      <p>新增后可按平台、渠道、状态和标签在本地搜索；详情页默认明文显示并可单字段复制。</p>
      <v-btn color="primary" prepend-icon="mdi-plus" @click="editor.openCreate()">
        添加第一条记录
      </v-btn>
    </section>

    <v-snackbar v-model="snackbar" location="bottom" :timeout="2000">{{ snackbarText }}</v-snackbar>
  </section>
</template>
