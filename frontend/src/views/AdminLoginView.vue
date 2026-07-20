<template>
  <AuthShell>
    <h1 class="text-h5 font-weight-semibold mb-2">管理员登录</h1>
    <p class="text-body-2 text-medium-emphasis mb-6">管理员与个人用户使用独立的认证入口和权限边界。</p>
    <v-alert type="info" variant="tonal" density="comfortable">
      管理员账户创建与管理员会话接口将在管理端功能阶段接入；当前个人用户注册和登录已可使用。
    </v-alert>
    <div class="mt-6 text-center"><router-link to="/login" class="text-primary font-weight-medium">返回个人用户登录</router-link></div>
  </AuthShell>
</template>

<script setup lang="ts">
import AuthShell from '@/components/AuthShell.vue'
</script>
