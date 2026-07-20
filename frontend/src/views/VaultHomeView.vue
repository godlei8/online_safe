<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const signingOut = ref(false)

onMounted(() => {
  if (!auth.session.authenticated) router.replace('/login')
})

async function logout() {
  signingOut.value = true
  try { await auth.logout(); await router.replace('/login') } finally { signingOut.value = false }
}
</script>

<template>
  <div class="auth-page pa-6 pa-sm-12">
    <header class="d-flex align-center justify-space-between mx-auto" style="max-width: 1120px">
      <div class="auth-brand"><v-icon icon="mdi-shield-lock-outline" class="auth-brand__mark mr-2" />Online Safe</div>
      <v-btn variant="text" color="secondary" :loading="signingOut" @click="logout">退出登录</v-btn>
    </header>
    <main class="mx-auto mt-14" style="max-width: 760px">
      <v-card class="pa-7 pa-sm-10" border rounded="xl" elevation="0">
        <v-icon icon="mdi-lock-check-outline" color="primary" size="40" class="mb-5" />
        <h1 class="text-h4 font-weight-semibold mb-3">欢迎回来，{{ auth.session.username }}</h1>
        <p class="text-body-1 text-medium-emphasis mb-0">账户注册和登录已经完成。账密条目、浏览器端加密与动态字段将在下一阶段接入。</p>
      </v-card>
    </main>
  </div>
</template>
