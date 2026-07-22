<script setup lang="ts">
import { computed, ref } from 'vue'
import { useDisplay } from 'vuetify'
import { useRoute, useRouter } from 'vue-router'
import { useAdminAuthStore } from '@/stores/adminAuth'

const route = useRoute()
const router = useRouter()
const adminAuth = useAdminAuthStore()
const { mdAndUp } = useDisplay()
const drawer = ref(false)
const signingOut = ref(false)

const navItems = [
  { title: '总览', icon: 'mdi-view-dashboard-outline', to: '/admin' },
  { title: '用户管理', icon: 'mdi-account-group-outline', to: '/admin/users' },
  { title: '邀请码', icon: 'mdi-ticket-confirmation-outline', to: '/admin/invitations' },
]

const pageTitle = computed(() => String(route.meta.title || '管理后台'))
const pageSubtitle = computed(() => String(route.meta.subtitle || ''))
const adminName = computed(() => adminAuth.session.username || 'admin')
const adminInitial = computed(() => adminName.value.trim().slice(0, 1).toUpperCase() || 'A')

async function logout() {
  signingOut.value = true
  try {
    await adminAuth.logout()
    await router.replace('/admin/login')
  } finally {
    signingOut.value = false
  }
}
</script>

<template>
  <div class="admin-shell">
    <aside v-if="mdAndUp" class="admin-sidebar">
      <div class="admin-sidebar__brand">
        <span class="admin-sidebar__mark"><v-icon icon="mdi-shield-lock" size="20" /></span>
        <div>
          <div class="admin-sidebar__name">Online Safe</div>
          <div class="admin-sidebar__tag">企业级安全管理</div>
        </div>
      </div>

      <nav class="admin-sidebar__nav">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="admin-nav-item"
          :class="{ 'admin-nav-item--active': route.path === item.to }"
        >
          <i class="admin-nav-item__icon mdi" :class="item.icon" aria-hidden="true" />
          <span>{{ item.title }}</span>
        </router-link>
      </nav>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div class="admin-topbar__left">
          <v-btn
            v-if="!mdAndUp"
            icon="mdi-menu"
            variant="text"
            aria-label="打开导航"
            @click="drawer = true"
          />
          <div>
            <h1 class="admin-topbar__title">{{ pageTitle }}</h1>
            <p v-if="pageSubtitle" class="admin-topbar__subtitle">{{ pageSubtitle }}</p>
          </div>
        </div>
        <div class="admin-topbar__right">
          <v-menu location="bottom end">
            <template #activator="{ props }">
              <button
                type="button"
                class="admin-user-chip"
                v-bind="props"
                :aria-label="`管理员 ${adminName}`"
              >
                <v-avatar color="primary" size="32" class="admin-user-chip__avatar">{{ adminInitial }}</v-avatar>
                <span class="admin-topbar__user">{{ adminName }}</span>
                <v-icon icon="mdi-chevron-down" size="18" />
              </button>
            </template>
            <v-list density="compact" min-width="180">
              <v-list-item :title="adminName" subtitle="管理员" prepend-icon="mdi-shield-account-outline" />
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

      <main class="admin-content">
        <router-view />
        <p class="admin-boundary">用户数据在客户端加密，管理员无法查看明文内容。</p>
      </main>
    </div>

    <v-navigation-drawer v-if="!mdAndUp" v-model="drawer" temporary color="surface">
      <div class="admin-sidebar__brand pa-4">
        <span class="admin-sidebar__mark"><v-icon icon="mdi-shield-lock" size="20" /></span>
        <div>
          <div class="admin-sidebar__name">Online Safe</div>
          <div class="admin-sidebar__tag">企业级安全管理</div>
        </div>
      </div>
      <v-list nav bg-color="transparent">
        <v-list-item
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          :prepend-icon="item.icon"
          :title="item.title"
          :active="route.path === item.to"
          color="primary"
          @click="drawer = false"
        />
      </v-list>
    </v-navigation-drawer>
  </div>
</template>
