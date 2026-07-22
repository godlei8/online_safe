<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { useRoute, useRouter } from 'vue-router'
import VaultItemFormDialog from '@/components/vault/VaultItemFormDialog.vue'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useAnnouncementsStore } from '@/stores/announcements'
import { useAuthStore } from '@/stores/auth'
import { useVaultStore } from '@/stores/vault'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const vault = useVaultStore()
const announcements = useAnnouncementsStore()
const editor = useVaultItemEditor()
const { mdAndUp } = useDisplay()

const signingOut = ref(false)
const navigationDrawer = ref(false)
const selectedAnnouncementId = ref<string | null>(null)

const userInitial = computed(() => (auth.session.username?.trim().slice(0, 1) || '我').toUpperCase())
const activeNav = computed(() => {
  if (route.name === 'vault-templates') return 'templates'
  return 'vault'
})

const selectedAnnouncement = computed(() => {
  if (!selectedAnnouncementId.value) return null
  return announcements.items.find((item) => item.id === selectedAnnouncementId.value) ?? null
})

async function logout() {
  signingOut.value = true
  try {
    await auth.logout()
    await router.replace('/login')
  } finally {
    signingOut.value = false
  }
}

function consumeEditorQuery() {
  if (!vault.ready) return
  const editId = typeof route.query.edit === 'string' ? route.query.edit : null
  const templateId = typeof route.query.templateId === 'string' ? route.query.templateId : null
  const isNew = route.query.new === '1' || route.query.new === 'true'

  if (editId) {
    editor.openEdit(editId)
  } else if (templateId) {
    editor.openCreate({ templateId })
  } else if (isNew) {
    editor.openCreate()
  } else {
    return
  }

  const nextQuery = { ...route.query }
  delete nextQuery.edit
  delete nextQuery.templateId
  delete nextQuery.new
  router.replace({ path: route.path, query: nextQuery })
}

function onSaved(id: string, _created: boolean) {
  if (route.name === 'vault-item' && String(route.params.id) === id) {
    router.replace({ path: `/vault/items/${id}`, query: { refreshed: String(Date.now()) } })
  }
}

async function openAnnouncements() {
  selectedAnnouncementId.value = null
  await announcements.openList()
}

function selectAnnouncement(id: string) {
  selectedAnnouncementId.value = id
}

async function markSelectedRead() {
  if (!selectedAnnouncementId.value) return
  await announcements.markRead(selectedAnnouncementId.value)
}

function formatPublishedAt(value: string | null) {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN')
}

onMounted(async () => {
  if (!auth.session.authenticated) {
    router.replace('/login')
    return
  }
  consumeEditorQuery()
  try {
    await announcements.refreshInbox()
  } catch {
    // 公告失败不阻断保险箱主流程
  }
})

watch(
  () => [route.fullPath, vault.ready] as const,
  () => {
    if (route.query.edit || route.query.templateId || route.query.new) {
      consumeEditorQuery()
    }
  },
)
</script>

<template>
  <div class="vault-shell">
    <aside class="vault-sidebar" aria-label="保险箱导航">
      <router-link to="/vault" class="vault-brand text-decoration-none" aria-label="Online Safe 保险箱首页">
        <span class="vault-brand__mark"><v-icon icon="mdi-shield-lock-outline" size="18" /></span>
        <span>Online Safe</span>
      </router-link>

      <nav class="vault-nav" aria-label="主要导航">
        <router-link
          class="vault-nav__item"
          :class="{ 'vault-nav__item--active': activeNav === 'vault' }"
          to="/vault"
          active-class=""
          exact-active-class=""
        >
          <v-icon icon="mdi-safe-square-outline" size="19" />
          <span>保险箱</span>
        </router-link>
        <router-link
          class="vault-nav__item"
          :class="{ 'vault-nav__item--active': activeNav === 'templates' }"
          to="/vault/templates"
          active-class=""
          exact-active-class=""
        >
          <v-icon icon="mdi-view-grid-plus-outline" size="19" />
          <span>模板</span>
        </router-link>
        <button class="vault-nav__item" type="button" disabled aria-label="标签，即将开放">
          <v-icon icon="mdi-tag-outline" size="19" />
          <span>标签<small>即将开放</small></span>
        </button>
        <button class="vault-nav__item" type="button" disabled aria-label="安全设置，即将开放">
          <v-icon icon="mdi-shield-cog-outline" size="19" />
          <span>安全设置<small>即将开放</small></span>
        </button>
      </nav>

      <div class="vault-sidebar__bottom">
        <button class="vault-nav__item" type="button" disabled aria-label="帮助中心，即将开放">
          <v-icon icon="mdi-help-circle-outline" size="19" />
          <span>帮助中心<small>即将开放</small></span>
        </button>
      </div>
    </aside>

    <main class="vault-main">
      <header class="vault-topbar">
        <div class="vault-topbar__mobile-brand">
          <v-btn icon="mdi-menu" variant="text" aria-label="打开导航" @click="navigationDrawer = true" />
          <span>Online Safe</span>
        </div>
        <span class="vault-topbar__page" aria-label="当前页面">
          {{ activeNav === 'templates' ? '模板' : '保险箱' }}
        </span>
        <div class="vault-topbar__spacer" />
        <div class="vault-topbar__actions">
          <span class="vault-lock-status">
            <v-icon icon="mdi-shield-check-outline" size="15" />
            已登录
          </span>
          <v-btn
            class="vault-bell-btn"
            icon
            variant="text"
            aria-label="查看公告"
            @click="openAnnouncements"
          >
            <v-badge
              :model-value="announcements.hasUnread"
              color="error"
              dot
              location="top end"
            >
              <v-icon icon="mdi-bell-outline" />
            </v-badge>
          </v-btn>
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

      <div class="vault-panel">
        <router-view v-if="vault.ready" />
        <div v-else class="pa-8 text-medium-emphasis">正在加载保险箱…</div>
      </div>

      <nav class="vault-mobile-nav" aria-label="移动端导航">
        <router-link
          class="vault-mobile-nav__item"
          :class="{ 'vault-mobile-nav__item--active': activeNav === 'vault' }"
          to="/vault"
          active-class=""
          exact-active-class=""
        >
          <v-icon icon="mdi-safe-square-outline" size="21" />
          <span>保险箱</span>
        </router-link>
        <router-link
          class="vault-mobile-nav__item"
          :class="{ 'vault-mobile-nav__item--active': activeNav === 'templates' }"
          to="/vault/templates"
          active-class=""
          exact-active-class=""
        >
          <v-icon icon="mdi-view-grid-plus-outline" size="21" />
          <span>模板</span>
        </router-link>
      </nav>
    </main>

    <v-navigation-drawer v-if="!mdAndUp" v-model="navigationDrawer" temporary color="surface">
      <div class="vault-drawer__brand">
        <span class="vault-brand__mark"><v-icon icon="mdi-shield-lock-outline" size="18" /></span>
        <div><strong>Online Safe</strong><small>个人保险箱</small></div>
      </div>
      <v-list nav bg-color="transparent">
        <v-list-item to="/vault" prepend-icon="mdi-safe-square-outline" title="保险箱" color="primary" @click="navigationDrawer = false" />
        <v-list-item to="/vault/templates" prepend-icon="mdi-view-grid-plus-outline" title="模板" color="primary" @click="navigationDrawer = false" />
        <v-list-item prepend-icon="mdi-tag-outline" title="标签" subtitle="即将开放" disabled />
        <v-list-item prepend-icon="mdi-shield-cog-outline" title="安全设置" subtitle="即将开放" disabled />
      </v-list>
    </v-navigation-drawer>

    <v-navigation-drawer
      v-model="announcements.listOpen"
      class="vault-announcement-drawer"
      location="right"
      temporary
      width="380"
      color="surface"
    >
      <header class="vault-announcement-drawer__header">
        <div class="vault-announcement-drawer__brand">
          <span class="vault-announcement-drawer__mark" aria-hidden="true">
            <v-icon icon="mdi-bullhorn-outline" size="18" />
          </span>
          <div>
            <strong>系统公告</strong>
            <small>
              <template v-if="announcements.inbox.unreadCount > 0">
                {{ announcements.inbox.unreadCount }} 条未读
              </template>
              <template v-else>已全部读完</template>
            </small>
          </div>
        </div>
        <v-btn icon="mdi-close" variant="text" aria-label="关闭公告列表" @click="announcements.listOpen = false" />
      </header>

      <v-progress-linear v-if="announcements.loadingList" indeterminate color="primary" />

      <div v-else class="vault-announcement-drawer__list">
        <button
          v-for="(item, index) in announcements.items"
          :key="item.id"
          type="button"
          class="vault-announcement-item"
          :class="{ 'vault-announcement-item--unread': !item.read }"
          :style="{ '--announce-delay': `${Math.min(index, 8) * 40}ms` }"
          @click="selectAnnouncement(item.id)"
        >
          <span class="vault-announcement-item__icon" aria-hidden="true">
            <v-icon :icon="item.read ? 'mdi-email-open-outline' : 'mdi-email-alert-outline'" size="18" />
          </span>
          <span class="vault-announcement-item__body">
            <span class="vault-announcement-item__title-row">
              <span class="vault-announcement-item__title">{{ item.title }}</span>
              <span v-if="!item.read" class="vault-announcement-item__badge">未读</span>
              <span v-else-if="item.pinned" class="vault-announcement-item__badge vault-announcement-item__badge--muted">置顶</span>
            </span>
            <span class="vault-announcement-item__meta">
              {{ item.read ? '已读' : '待确认' }}
              <template v-if="item.publishedAt"> · {{ formatPublishedAt(item.publishedAt) }}</template>
            </span>
            <span class="vault-announcement-item__excerpt">{{ item.body }}</span>
          </span>
          <v-icon class="vault-announcement-item__chevron" icon="mdi-chevron-right" size="18" />
        </button>

        <div v-if="announcements.items.length === 0" class="vault-announcement-empty">
          <span class="vault-announcement-empty__mark" aria-hidden="true">
            <v-icon icon="mdi-bell-check-outline" size="22" />
          </span>
          <strong>暂无公告</strong>
          <p>有新公告时会在这里提醒你</p>
        </div>
      </div>
    </v-navigation-drawer>

    <v-dialog
      :model-value="Boolean(selectedAnnouncement)"
      class="vault-announcement-dialog"
      max-width="540"
      transition="dialog-bottom-transition"
      @update:model-value="(open) => { if (!open) selectedAnnouncementId = null }"
    >
      <v-card v-if="selectedAnnouncement" class="vault-announcement-sheet">
        <div class="vault-announcement-sheet__hero">
          <span class="vault-announcement-sheet__hero-icon" aria-hidden="true">
            <v-icon icon="mdi-bullhorn-outline" size="20" />
          </span>
          <div>
            <p class="vault-announcement-sheet__eyebrow">
              {{ selectedAnnouncement.read ? '已读公告' : '未读公告' }}
              <template v-if="selectedAnnouncement.pinned"> · 置顶</template>
            </p>
            <h2>{{ selectedAnnouncement.title }}</h2>
            <time v-if="selectedAnnouncement.publishedAt">{{ formatPublishedAt(selectedAnnouncement.publishedAt) }}</time>
          </div>
        </div>
        <div class="vault-announcement-sheet__body">{{ selectedAnnouncement.body }}</div>
        <div class="vault-announcement-sheet__actions">
          <v-btn
            v-if="!selectedAnnouncement.read"
            color="primary"
            @click="markSelectedRead"
          >
            标为已读
          </v-btn>
          <v-btn variant="tonal" color="primary" @click="selectedAnnouncementId = null">关闭</v-btn>
        </div>
      </v-card>
    </v-dialog>

    <v-dialog
      :model-value="announcements.forceOpen"
      class="vault-announcement-dialog"
      persistent
      max-width="540"
      :scrim="true"
      transition="dialog-transition"
    >
      <v-card v-if="announcements.latestUnread" class="vault-announcement-sheet vault-announcement-sheet--force">
        <div class="vault-announcement-sheet__hero">
          <span class="vault-announcement-sheet__hero-icon vault-announcement-sheet__hero-icon--pulse" aria-hidden="true">
            <v-icon icon="mdi-bell-ring-outline" size="20" />
          </span>
          <div>
            <p class="vault-announcement-sheet__eyebrow">需要确认的公告</p>
            <h2>{{ announcements.latestUnread.title }}</h2>
            <time v-if="announcements.latestUnread.publishedAt">
              {{ formatPublishedAt(announcements.latestUnread.publishedAt) }}
            </time>
          </div>
        </div>
        <div class="vault-announcement-sheet__body">{{ announcements.latestUnread.body }}</div>
        <div class="vault-announcement-sheet__actions">
          <v-btn
            color="primary"
            block
            size="large"
            :loading="announcements.acknowledging"
            @click="announcements.acknowledgeLatestUnread()"
          >
            我知道了
          </v-btn>
        </div>
      </v-card>
    </v-dialog>

    <VaultItemFormDialog @saved="onSaved" />
  </div>
</template>
