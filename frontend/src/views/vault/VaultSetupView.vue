<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { peekEphemeralLoginPassword, takeEphemeralLoginPassword } from '@/stores/auth'
import { useVaultStore } from '@/stores/vault'

const router = useRouter()
const vault = useVaultStore()
const loginPassword = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const bootstrapping = ref(true)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})
const needsPasswordInput = ref(false)
const setupDone = ref(false)

onMounted(async () => {
  if (vault.initialized && vault.dekReady) {
    await router.replace('/vault')
    return
  }
  const held = peekEphemeralLoginPassword()
  if (held) {
    await runSetup(held)
  } else {
    needsPasswordInput.value = true
  }
  bootstrapping.value = false
})

async function runSetup(password: string) {
  submitting.value = true
  errorMessage.value = ''
  try {
    await vault.setup(password)
    takeEphemeralLoginPassword()
    setupDone.value = true
    needsPasswordInput.value = false
    await router.replace('/vault')
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    errorMessage.value =
      message && /[\u4e00-\u9fff]/.test(message) ? message : '初始化保险箱失败，请稍后重试'
    needsPasswordInput.value = true
    setupDone.value = false
  } finally {
    submitting.value = false
  }
}

async function submitPassword() {
  errorMessage.value = ''
  fieldErrors.value = {}
  if (!loginPassword.value) {
    fieldErrors.value.loginPassword = '请输入当前登录密码'
    return
  }
  await runSetup(loginPassword.value)
  loginPassword.value = ''
}
</script>

<template>
  <div class="vault-gate">
    <v-card class="vault-gate__card" elevation="0">
      <div class="vault-gate__brand">
        <v-icon icon="mdi-shield-key-outline" size="26" />
        <span>Online Safe</span>
      </div>

      <template v-if="bootstrapping || (submitting && !needsPasswordInput)">
        <div class="auth-form-heading">
          <span class="auth-form-heading__eyebrow">首次使用</span>
          <h1>正在初始化保险箱…</h1>
          <p>将使用你的登录密码在本机创建密钥信封，请稍候。</p>
        </div>
        <v-progress-linear indeterminate color="primary" class="mt-4" />
      </template>

      <template v-else-if="needsPasswordInput && !setupDone">
        <div class="auth-form-heading">
          <span class="auth-form-heading__eyebrow">首次使用</span>
          <h1>初始化保险箱</h1>
          <p>确认你的登录密码后，将在本机生成加密密钥。登录成功即可直接使用保险箱，无需额外步骤。</p>
        </div>

        <aside class="vault-gate__tips" aria-label="初始化说明">
          <div class="vault-gate__tips-title">
            <v-icon icon="mdi-information-outline" size="18" />
            开始前请了解
          </div>
          <ul class="vault-gate__tips-list">
            <li>
              <v-icon icon="mdi-lock-outline" size="16" />
              <span>登录密码仅在本机用于包装数据密钥，不会以明文上传。</span>
            </li>
            <li>
              <v-icon icon="mdi-alert-outline" size="16" />
              <span>通过密保重置登录密码后，需重新初始化空保险箱，旧密文将无法保留。</span>
            </li>
          </ul>
        </aside>

        <form class="vault-gate__form" @submit.prevent="submitPassword">
          <v-text-field
            v-model="loginPassword"
            label="确认登录密码"
            placeholder="请输入当前登录密码"
            prepend-inner-icon="mdi-lock-outline"
            autocomplete="current-password"
            :type="showPassword ? 'text' : 'password'"
            :error-messages="fieldErrors.loginPassword"
            @update:model-value="delete fieldErrors.loginPassword"
          >
            <template #append-inner>
              <v-btn
                class="password-toggle"
                :icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
                variant="text"
                density="compact"
                :aria-label="showPassword ? '隐藏登录密码' : '显示登录密码'"
                @click="showPassword = !showPassword"
              />
            </template>
          </v-text-field>

          <v-alert
            v-if="errorMessage"
            type="error"
            variant="tonal"
            density="comfortable"
            class="auth-form-alert mt-4 mb-0"
            role="alert"
          >
            {{ errorMessage }}
          </v-alert>

          <div class="vault-gate__actions mt-6">
            <v-btn
              type="submit"
              color="primary"
              block
              class="auth-submit"
              :loading="submitting"
              :disabled="submitting"
            >
              {{ submitting ? '正在初始化…' : '初始化保险箱' }}
            </v-btn>
          </div>
        </form>
      </template>
    </v-card>
  </div>
</template>
