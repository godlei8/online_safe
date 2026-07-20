<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useDisplay } from 'vuetify'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { smAndDown } = useDisplay()
const initializationError = ref('')
const showInitializationError = ref(false)
const retryingConnection = ref(false)

async function initialize() {
  try {
    await auth.bootstrap()
    initializationError.value = ''
    showInitializationError.value = false
  } catch {
    initializationError.value = '暂时无法连接本地服务，部分操作可能不可用。'
    showInitializationError.value = true
    auth.ready = true
  }
}

async function retryConnection() {
  retryingConnection.value = true
  try {
    await initialize()
  } finally {
    retryingConnection.value = false
  }
}

onMounted(initialize)
</script>

<template>
  <v-app>
    <v-main>
      <router-view v-if="auth.ready" />
      <div v-else class="d-flex align-center justify-center" style="min-height: 100vh">
        <v-progress-circular indeterminate color="primary" aria-label="正在初始化会话" />
      </div>
      <v-snackbar v-model="showInitializationError" color="warning" :location="smAndDown ? 'bottom' : 'top'" :timeout="-1" class="connection-snackbar">
        <div class="d-flex align-center">
          <v-icon icon="mdi-wifi-off" class="mr-2" />
          <span>{{ initializationError }}</span>
        </div>
        <template #actions>
          <v-btn variant="text" :loading="retryingConnection" @click="retryConnection">重新连接</v-btn>
          <v-btn icon="mdi-close" variant="text" aria-label="关闭提示" @click="showInitializationError = false" />
        </template>
      </v-snackbar>
    </v-main>
  </v-app>
</template>
