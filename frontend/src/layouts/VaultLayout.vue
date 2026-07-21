<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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

const signingOut = ref(false)
const showNavigationHint = ref(false)
const navigationHint = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const confirming = ref(false)
const confirmError = ref('')

const userInitial = computed(() => (auth.session.username?.trim().slice(0, 1) || '我').toUpperCase())
const activeNav = computed(() => {
  if (route.name === 'vault-templates') return 'templates'
  return 'vault'
})
const showConfirmDialog = computed(() => vault.initialized === true && !vault.dekReady)

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

async function submitConfirmPassword() {
  confirmError.value = ''
  if (!confirmPassword.value) {
    confirmError.value = '请输入登录密码'
    return
  }
  confirming.value = true
  try {
    await vault.openWithPassword(confirmPassword.value)
    confirmPassword.value = ''
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    confirmError.value =
      message && /[\u4e00-\u9fff]/.test(message) ? message : '登录密码不正确，请重试'
  } finally {
    confirming.value = false
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

function onSaved(id: string) {
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
        <div v-else class="pa-8 text-medium-emphasis">
          会话仍有效。请确认登录密码以继续查看账密。
        </div>
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
        <button class="vault-mobile-nav__item" type="button" @click="notifyUnavailable('标签')">
          <v-icon icon="mdi-tag-outline" size="21" />
          <span>标签</span>
        </button>
        <button class="vault-mobile-nav__item" type="button" @click="logout">
          <v-icon icon="mdi-logout" size="21" />
          <span>退出</span>
        </button>
      </nav>
    </main>

    <v-dialog :model-value="showConfirmDialog" persistent max-width="440">
      <v-card class="pa-2">
        <v-card-title class="text-h6">确认登录密码以继续</v-card-title>
        <v-card-text>
          <p class="mb-4">
            页面刷新后内存中的密钥已清除。请再次输入登录密码以继续查看账密（这不是单独的保险箱解锁步骤）。
          </p>
          <v-text-field
            v-model="confirmPassword"
            label="登录密码"
            placeholder="请输入登录密码"
            autocomplete="current-password"
            :type="showPassword ? 'text' : 'password'"
            :error-messages="confirmError"
            @keyup.enter="submitConfirmPassword"
          >
            <template #append-inner>
              <v-btn
                class="password-toggle"
                :icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
                variant="text"
                density="compact"
                @click="showPassword = !showPassword"
              />
            </template>
          </v-text-field>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-btn variant="text" @click="logout">退出登录</v-btn>
          <v-spacer />
          <v-btn color="primary" :loading="confirming" :disabled="confirming" @click="submitConfirmPassword">
            继续查看
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <VaultItemFormDialog @saved="onSaved" />
    <v-snackbar v-model="showNavigationHint" color="secondary" location="bottom" :timeout="2800">
      {{ navigationHint }}
    </v-snackbar>
  </div>
</template>
