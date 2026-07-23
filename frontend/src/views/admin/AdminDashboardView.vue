<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { usersApi, type ManagedUserStats } from '@/api/users'

const quickLinks = [
  { title: '用户管理', to: '/admin/users', icon: 'mdi-account-group-outline' },
  { title: '创建邀请码', to: '/admin/invitations', icon: 'mdi-key-plus' },
  { title: '发布公告', to: '/admin/announcements', icon: 'mdi-bullhorn-outline' },
  { title: '系统模板', to: '/admin/templates', icon: 'mdi-view-grid-plus-outline' },
]

const userStats = ref<ManagedUserStats | null>(null)

onMounted(async () => {
  try {
    userStats.value = await usersApi.stats()
  } catch {
    userStats.value = null
  }
})
</script>

<template>
  <div class="admin-dashboard">
    <v-alert type="info" variant="tonal" class="mb-6">
      保险箱、邀请码、用户管理、公告与系统模板已开放；安全日志将陆续接入。
    </v-alert>

    <div class="admin-stat-grid mb-4">
      <v-card class="admin-stat-card admin-stat-card--primary" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">用户总数</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-account-group-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ userStats ? userStats.total : '—' }}</div>
        <div class="admin-stat-card__hint">详见用户管理页</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--warning" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">近 7 日活跃</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-chart-timeline-variant" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">{{ userStats ? userStats.activeLast7Days : '—' }}</div>
        <div class="admin-stat-card__hint">按最近登录统计</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--info" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">保险箱存储</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-database-lock-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">已接入</div>
        <div class="admin-stat-card__hint">服务端 AES-GCM 加密入库</div>
      </v-card>
      <v-card class="admin-stat-card admin-stat-card--success" elevation="0">
        <div class="admin-stat-card__head">
          <div class="admin-stat-card__label">数据边界</div>
          <div class="admin-stat-card__icon" aria-hidden="true">
            <v-icon icon="mdi-check-decagram-outline" size="18" />
          </div>
        </div>
        <div class="admin-stat-card__value">隔离</div>
        <div class="admin-stat-card__hint">管理员不可查看用户明文</div>
      </v-card>
    </div>

    <v-card class="admin-panel" elevation="0">
      <div class="admin-panel__title mb-4">快捷入口</div>
      <div class="admin-quick-grid">
        <router-link v-for="item in quickLinks" :key="item.to" :to="item.to" class="admin-quick-item">
          <v-icon :icon="item.icon" color="primary" class="mb-2" />
          <span>{{ item.title }}</span>
        </router-link>
      </div>
    </v-card>
  </div>
</template>
