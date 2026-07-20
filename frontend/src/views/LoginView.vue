<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { z } from 'zod'
import AuthShell from '@/components/AuthShell.vue'
import { ApiRequestError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = ref()
const identifier = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

const schema = z.object({
  identifier: z.string().trim().min(1, '请输入手机号或用户名').max(64, '账号格式不正确'),
  password: z.string().min(1, '请输入登录密码').max(72, '密码格式不正确'),
})

const required = (message: string) => (value: string) => Boolean(value?.trim()) || message

onMounted(() => {
  if (auth.session.authenticated) router.replace('/vault')
})

function clearFieldError(field: string) {
  delete fieldErrors.value[field]
}

async function submit() {
  errorMessage.value = ''
  fieldErrors.value = {}
  const parsed = schema.safeParse({ identifier: identifier.value, password: password.value })
  if (!parsed.success) {
    const issue = parsed.error.issues[0]
    if (issue?.path[0]) fieldErrors.value[String(issue.path[0])] = issue.message
    return
  }
  const validation = await form.value?.validate()
  if (!validation?.valid) return

  submitting.value = true
  try {
    await auth.login(parsed.data)
    await router.replace('/vault')
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      fieldErrors.value = error.fieldErrors
    } else {
      errorMessage.value = '登录未完成，请稍后再试。'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell>
    <div class="auth-segmented mb-7" role="tablist" aria-label="登录入口切换">
      <router-link to="/login" class="auth-segmented__item auth-segmented__item--active" role="tab" aria-selected="true">个人登录</router-link>
      <router-link to="/admin/login" class="auth-segmented__item" role="tab" aria-selected="false">管理员入口</router-link>
    </div>

    <div class="auth-form-heading">
      <span class="auth-form-heading__eyebrow">欢迎回来</span>
      <h1>登录你的保险箱</h1>
      <p>请输入账号信息，继续管理你的记录。</p>
    </div>

    <v-alert v-if="errorMessage" type="error" variant="tonal" density="comfortable" class="auth-form-alert" role="alert">
      {{ errorMessage }}
    </v-alert>

    <v-form ref="form" validate-on="blur" @submit.prevent="submit">
      <v-text-field
        v-model="identifier"
        label="手机号或用户名"
        placeholder="请输入手机号或用户名"
        prepend-inner-icon="mdi-account-outline"
        autocomplete="username"
        :rules="[required('请输入手机号或用户名')]"
        :error-messages="fieldErrors.identifier"
        @update:model-value="clearFieldError('identifier')"
      />

      <v-text-field
        v-model="password"
        class="mt-2"
        label="登录密码"
        placeholder="请输入登录密码"
        prepend-inner-icon="mdi-lock-outline"
        autocomplete="current-password"
        :type="showPassword ? 'text' : 'password'"
        :rules="[required('请输入登录密码')]"
        :error-messages="fieldErrors.password"
        @update:model-value="clearFieldError('password')"
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

      <div class="auth-inline-note" role="note">
        <v-icon icon="mdi-information-outline" size="18" />
        <span>登录密码用于身份认证；保险箱主密码会在后续功能中独立处理。</span>
      </div>

      <v-btn type="submit" color="primary" block class="auth-submit" :loading="submitting" :disabled="submitting">
        {{ submitting ? '正在登录…' : '登录' }}
      </v-btn>
    </v-form>

    <p class="auth-alternate-action">还没有账号？<router-link to="/register">创建账号</router-link></p>
  </AuthShell>
</template>
