<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { useRoute, useRouter } from 'vue-router'
import VaultItemFormDialog from '@/components/vault/VaultItemFormDialog.vue'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useAuthStore } from '@/stores/auth'
import { useVaultStore } from '@/stores/vault'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const vault = useVaultStore()
const editor = useVaultItemEditor()
const { mdAndUp } = useDisplay()

const signingOut = ref(false)
const navigationDrawer = ref(false)
const showNavigationHint = ref(false)
const navigationHint = ref('')

const userInitial = computed(() => (auth.session.username?.trim().slice(0, 1) || '我').toUpperCase())
const activeNav = computed(() => {
  if (route.name === 'vault-templates') return 'templates'
  return 'vault'
})

function notifyUnavailable(name: string) {
  navigationHint.value = `${name}正在开发中。`
  showNavigationHint.value = true
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

function consumeEditorQuery() {
  if (!vault.dekReady) return
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

function onSaved(id: string, created: boolean) {
  // 新增成功：关闭弹窗即可，不跳转详情
  if (created) return
  if (route.name === 'vault-item' && String(route.params.id) === id) {
    router.replace({ path: `/vault/items/${id}`, query: { refreshed: String(Date.now()) } })
    return
  }
  router.push(`/vault/items/${id}`)
}

onMounted(() => {
  if (!auth.session.authenticated) {
    router.replace('/login')
    return
  }
  consumeEditorQuery()
})

watch(
  () => [route.fullPath, vault.dekReady] as const,
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
            已登录 · 详情默认明文
          </span>
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

      <div class="vault-panel">
        <router-view v-if="vault.dekReady" />
        <div v-else class="pa-8 text-medium-emphasis">正在准备保险箱…</div>
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
        <button class="vault-mobile-nav__item" type="button" @click="logout">
          <v-icon icon="mdi-logout" size="21" />
          <span>退出</span>
        </button>
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

    <VaultItemFormDialog @saved="onSaved" />
    <v-snackbar v-model="showNavigationHint" class="vault-feedback-snackbar" color="primary" location="bottom" :timeout="2800">
      {{ navigationHint }}
    </v-snackbar>
  </div>
</template>
