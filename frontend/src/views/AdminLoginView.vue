<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { z } from 'zod'
import AuthShell from '@/components/AuthShell.vue'
import { ApiRequestError } from '@/api/client'
import { useAdminAuthStore } from '@/stores/adminAuth'

const router = useRouter()
const adminAuth = useAdminAuthStore()
const form = ref()
const username = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

const schema = z.object({
  username: z.string().trim().min(1, '请输入管理员用户名').max(64, '用户名格式不正确'),
  password: z.string().min(1, '请输入登录密码').max(72, '密码格式不正确'),
})

const required = (message: string) => (value: string) => Boolean(value?.trim()) || message

onMounted(async () => {
  if (!adminAuth.ready) {
    try {
      await adminAuth.bootstrap()
    } catch {
      /* 连接失败时仍展示登录表单 */
    }
  }
  if (adminAuth.session.authenticated) {
    await router.replace('/admin')
  }
})

function clearFieldError(field: string) {
  delete fieldErrors.value[field]
}

async function submit() {
  errorMessage.value = ''
  fieldErrors.value = {}
  const parsed = schema.safeParse({ username: username.value, password: password.value })
  if (!parsed.success) {
    const issue = parsed.error.issues[0]
    if (issue?.path[0]) fieldErrors.value[String(issue.path[0])] = issue.message
    return
  }
  const validation = await form.value?.validate()
  if (!validation?.valid) return

  submitting.value = true
  try {
    await adminAuth.login(parsed.data)
    await router.replace('/admin')
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
      <router-link to="/login" class="auth-segmented__item" role="tab" aria-selected="false">个人登录</router-link>
      <router-link to="/admin/login" class="auth-segmented__item auth-segmented__item--active" role="tab" aria-selected="true">管理员入口</router-link>
    </div>

    <div class="auth-form-heading">
      <span class="auth-form-heading__eyebrow">管理后台</span>
      <h1>管理员登录</h1>
      <p>使用系统内置管理员账号进入后台，管理邀请码与系统配置。</p>
    </div>

    <v-alert v-if="errorMessage" type="error" variant="tonal" density="comfortable" class="auth-form-alert" role="alert">
      {{ errorMessage }}
    </v-alert>

    <v-form ref="form" class="auth-login-form" validate-on="blur" @submit.prevent="submit">
      <v-text-field
        v-model="username"
        label="管理员用户名"
        placeholder="请输入管理员用户名"
        prepend-inner-icon="mdi-shield-account-outline"
        autocomplete="username"
        :rules="[required('请输入管理员用户名')]"
        :error-messages="fieldErrors.username"
        @update:model-value="clearFieldError('username')"
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

      <v-btn class="auth-submit" type="submit" color="primary" block :loading="submitting">进入管理后台</v-btn>
    </v-form>

    <div class="mt-6 text-center">
      <router-link to="/login" class="text-primary font-weight-medium">返回个人用户登录</router-link>
    </div>
  </AuthShell>
</template>
