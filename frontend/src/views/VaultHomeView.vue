<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

type 记录状态 = '安全' | '需关注' | '已归档'
type 排序方式 = '最近更新' | '最早创建' | '即将到期'
type 展示方式 = '卡片' | '列表'

type 保险箱记录 = {
  id: string
  名称: string
  平台: string
  渠道: string
  状态: 记录状态
  标签: string[]
  更新时间: string
  更新时间戳: number
}

const router = useRouter()
const auth = useAuthStore()
const signingOut = ref(false)
const searchKeyword = ref('')
const selectedPlatform = ref('全部平台')
const selectedChannel = ref('全部渠道')
const selectedStatus = ref('全部状态')
const sortBy = ref<排序方式>('最近更新')
const displayMode = ref<展示方式>('卡片')
const showNewRecordDialog = ref(false)
const showNavigationHint = ref(false)
const navigationHint = ref('')

// 首页仅展示元数据；真实记录将在保险箱加密与记录接口接入后从服务端读取。
const records = ref<保险箱记录[]>([])

const platformOptions = computed(() => ['全部平台', ...new Set(records.value.map((item) => item.平台))])
const channelOptions = computed(() => ['全部渠道', ...new Set(records.value.map((item) => item.渠道))])
const statusOptions = ['全部状态', '安全', '需关注', '已归档']
const sortOptions: 排序方式[] = ['最近更新', '最早创建', '即将到期']

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
    if (sortBy.value === '即将到期') return left.名称.localeCompare(right.名称, 'zh-CN')
    return right.更新时间戳 - left.更新时间戳
  })
})

const hasActiveFilters = computed(() => (
  Boolean(searchKeyword.value.trim())
  || selectedPlatform.value !== '全部平台'
  || selectedChannel.value !== '全部渠道'
  || selectedStatus.value !== '全部状态'
))

const userInitial = computed(() => (auth.session.username?.trim().slice(0, 1) || '我').toUpperCase())

function resetFilters() {
  searchKeyword.value = ''
  selectedPlatform.value = '全部平台'
  selectedChannel.value = '全部渠道'
  selectedStatus.value = '全部状态'
}

function notifyUnavailable(name: string) {
  navigationHint.value = `${name}正在开发中，当前已完成保险箱首页。`
  showNavigationHint.value = true
}

function openRecord() {
  notifyUnavailable('记录详情')
}

function statusColor(status: 记录状态) {
  return status === '安全' ? 'success' : status === '需关注' ? 'warning' : 'secondary'
}

async function logout() {
  signingOut.value = true
  try {
    await auth.logout()
    await router.replace('/login')
  } finally {
    signingOut.value = false
  }
}

onMounted(() => {
  if (!auth.session.authenticated) router.replace('/login')
})
</script>

<template>
  <div class="vault-shell">
    <!-- 电脑端导航在左侧栏；底部四格仅手机端显示（≥960px 隐藏 .vault-mobile-nav） -->
    <aside class="vault-sidebar" aria-label="保险箱导航">
      <router-link to="/vault" class="vault-brand text-decoration-none" aria-label="Online Safe 保险箱首页">
        <span class="vault-brand__mark"><v-icon icon="mdi-shield-lock-outline" size="18" /></span>
        <span>Online Safe</span>
      </router-link>

      <nav class="vault-nav" aria-label="主要导航">
        <button class="vault-nav__item vault-nav__item--active" type="button">
          <v-icon icon="mdi-safe-square-outline" size="19" />
          <span>保险箱</span>
        </button>
        <button class="vault-nav__item" type="button" @click="notifyUnavailable('模板')">
          <v-icon icon="mdi-view-grid-plus-outline" size="19" />
          <span>模板</span>
        </button>
        <button class="vault-nav__item" type="button" @click="notifyUnavailable('标签')">
          <v-icon icon="mdi-tag-outline" size="19" />
          <span>标签</span>
        </button>
        <button class="vault-nav__item" type="button" @click="notifyUnavailable('安全设置')">
          <v-icon icon="mdi-shield-cog-outline" size="19" />
          <span>安全设置</span>
        </button>
      </nav>

      <div class="vault-sidebar__bottom">
        <button class="vault-nav__item" type="button" @click="notifyUnavailable('帮助中心')">
          <v-icon icon="mdi-help-circle-outline" size="19" />
          <span>帮助中心</span>
        </button>
      </div>
    </aside>

    <main class="vault-main">
      <header class="vault-topbar">
        <div class="vault-topbar__mobile-brand">
          <v-btn icon="mdi-menu" variant="text" aria-label="打开导航" @click="notifyUnavailable('导航菜单')" />
          <span>Online Safe</span>
        </div>
        <div class="vault-topbar__spacer" />
        <div class="vault-topbar__actions">
          <span class="vault-lock-status"><v-icon icon="mdi-lock-outline" size="15" />敏感字段默认隐藏</span>
          <v-btn icon="mdi-bell-outline" variant="text" aria-label="查看通知" @click="notifyUnavailable('通知')" />
          <v-menu location="bottom end">
            <template #activator="{ props }">
              <button
                type="button"
                class="vault-user-chip"
                v-bind="props"
                :aria-label="`${auth.session.username ?? '当前用户'}的账户菜单`"
              >
                <v-avatar color="primary" size="32" class="vault-user-avatar">{{ userInitial }}</v-avatar>
                <span class="vault-user-chip__name">{{ auth.session.username ?? '用户' }}</span>
                <v-icon icon="mdi-chevron-down" size="18" />
              </button>
            </template>
            <v-list density="compact" min-width="180">
              <v-list-item
                :title="auth.session.username ?? '用户'"
                subtitle="个人账户"
                prepend-icon="mdi-account-outline"
              />
              <v-divider />
              <v-list-item
                prepend-icon="mdi-logout"
                title="退出登录"
                :disabled="signingOut"
                @click="logout"
              />
            </v-list>
          </v-menu>
        </div>
      </header>

      <section class="vault-content" aria-labelledby="vault-title">
        <div class="vault-title-row">
          <div>
            <p class="vault-eyebrow">你的私人空间</p>
            <div class="vault-title-row__heading">
              <h1 id="vault-title">我的保险箱</h1>
              <span>{{ filteredRecords.length }} 条记录</span>
            </div>
          </div>
          <v-btn color="primary" class="vault-new-record" prepend-icon="mdi-plus" @click="showNewRecordDialog = true">新增记录</v-btn>
        </div>

        <div class="vault-privacy-note" role="note">
          <v-icon icon="mdi-lock-check-outline" size="18" />
          <span>首页只展示记录元数据；账号、密码和其他敏感字段默认不显示。</span>
        </div>

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
              <v-btn :variant="displayMode === '卡片' ? 'tonal' : 'text'" icon="mdi-view-grid-outline" aria-label="卡片视图" @click="displayMode = '卡片'" />
              <v-btn :variant="displayMode === '列表' ? 'tonal' : 'text'" icon="mdi-format-list-bulleted" aria-label="列表视图" @click="displayMode = '列表'" />
            </div>
          </div>
        </div>

        <div v-if="filteredRecords.length" class="vault-records" :class="{ 'vault-records--list': displayMode === '列表' }">
          <button v-for="item in filteredRecords" :key="item.id" class="vault-record-card" type="button" @click="openRecord">
            <div class="vault-record-card__heading">
              <v-avatar size="38" rounded="lg" color="blue-lighten-5"><v-icon icon="mdi-key-variant" color="primary" size="19" /></v-avatar>
              <div>
                <strong>{{ item.名称 }}</strong>
                <span>{{ item.平台 }} · {{ item.渠道 }}</span>
              </div>
              <v-chip :color="statusColor(item.状态)" size="x-small" variant="tonal">{{ item.状态 }}</v-chip>
            </div>
            <div class="vault-record-card__hidden"><v-icon icon="mdi-eye-off-outline" size="16" />敏感字段已隐藏</div>
            <div class="vault-record-card__footer"><span>{{ item.标签.join(' · ') }}</span><time>{{ item.更新时间 }}</time></div>
          </button>
        </div>

        <section v-else class="vault-empty-state" aria-label="空保险箱提示">
          <div class="vault-empty-state__illustration" aria-hidden="true"><v-icon icon="mdi-safe-square-outline" size="42" /></div>
          <p class="vault-eyebrow">从第一条记录开始</p>
          <h2>你的保险箱还是空的</h2>
          <p>新增后，你可以按平台、渠道、状态和标签进行本地搜索与筛选；敏感字段会默认隐藏。</p>
          <v-btn color="primary" prepend-icon="mdi-plus" @click="showNewRecordDialog = true">添加第一条记录</v-btn>
        </section>
      </section>

      <nav class="vault-mobile-nav" aria-label="移动端导航">
        <button class="vault-mobile-nav__item vault-mobile-nav__item--active" type="button"><v-icon icon="mdi-safe-square-outline" size="21" /><span>保险箱</span></button>
        <button class="vault-mobile-nav__item" type="button" @click="notifyUnavailable('模板')"><v-icon icon="mdi-view-grid-plus-outline" size="21" /><span>模板</span></button>
        <button class="vault-mobile-nav__item" type="button" @click="notifyUnavailable('标签')"><v-icon icon="mdi-tag-outline" size="21" /><span>标签</span></button>
        <button class="vault-mobile-nav__item" type="button" @click="notifyUnavailable('安全设置')"><v-icon icon="mdi-shield-cog-outline" size="21" /><span>安全</span></button>
      </nav>
    </main>

    <v-dialog v-model="showNewRecordDialog" max-width="440">
      <v-card class="vault-dialog pa-6">
        <v-avatar color="blue-lighten-5" rounded="lg" size="46" class="mb-5"><v-icon icon="mdi-note-plus-outline" color="primary" /></v-avatar>
        <h2>新增记录</h2>
        <p>记录录入、浏览器端加密和动态字段接口正在接入。首页已预留新增入口，后续不会在首页展示敏感字段。</p>
        <div class="d-flex justify-end mt-6"><v-btn color="primary" @click="showNewRecordDialog = false">我知道了</v-btn></div>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="showNavigationHint" color="secondary" location="bottom" :timeout="2800">{{ navigationHint }}</v-snackbar>
  </div>
</template>
