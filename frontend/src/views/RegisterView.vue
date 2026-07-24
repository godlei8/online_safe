<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { z } from 'zod'
import AuthShell from '@/components/AuthShell.vue'
import { ApiRequestError } from '@/api/client'
import { authApi } from '@/api/auth'
import { useOsToast } from '@/composables/useOsToast'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const toast = useOsToast()
const currentStep = ref(1)
const phone = ref('')
const username = ref('')
const smsCode = ref('')
const password = ref('')
const confirmPassword = ref('')
const agreedToTerms = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const submitting = ref(false)
const sendingSms = ref(false)
const countdown = ref(0)
const errorMessage = ref('')
const accountErrors = ref<Record<string, string>>({})
const passwordErrors = ref<Record<string, string>>({})
const serverErrors = ref<Record<string, string>>({})

let countdownTimer: ReturnType<typeof setInterval> | null = null

const accountSchema = z.object({
  phone: z.string().trim().regex(/^(?:\+86|86)?1[3-9]\d{9}$/, '请输入有效的中国大陆手机号'),
  username: z.string().trim().regex(/^[\p{L}\p{N}_-]{3,32}$/u, '用户名为 3–32 位字母、数字、下划线或连字符'),
  smsCode: z.string().trim().regex(/^\d{4,8}$/, '请输入短信验证码'),
})

const passwordSchema = z.object({
  password: z.string().min(8, '登录密码至少 8 位').max(72, '登录密码不能超过 72 位'),
  confirmPassword: z.string().min(1, '请再次输入登录密码'),
  agreedToTerms: z.literal(true, { error: '请阅读并同意相关条款' }),
}).refine((value) => value.password === value.confirmPassword, {
  path: ['confirmPassword'],
  message: '两次输入的密码不一致',
})

const passwordRules = computed(() => [
  { label: '至少 8 位', passed: password.value.length >= 8 },
  { label: '两次输入一致', passed: confirmPassword.value.length > 0 && password.value === confirmPassword.value },
])

const stepTitle = computed(() => (currentStep.value === 1 ? '验证手机号' : '设置密码'))

const sendButtonLabel = computed(() => {
  if (sendingSms.value) return '发送中…'
  if (countdown.value > 0) return `${countdown.value}s 后可重发`
  return '获取验证码'
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

function collectErrors(result: ReturnType<typeof accountSchema.safeParse> | ReturnType<typeof passwordSchema.safeParse>, target: Record<string, string>, fields: string[]) {
  for (const field of fields) delete target[field]
  let fieldsOk = true
  if (!result.success) {
    for (const issue of result.error.issues) {
      const field = String(issue.path[0] ?? '')
      if (fields.includes(field) && !target[field]) {
        target[field] = issue.message
        fieldsOk = false
      }
    }
  }
  // 只按本次校验字段判定，避免「仅校验手机号」时因用户名/验证码为空被误判失败
  return fieldsOk
}

function validateAccount(fields = ['phone', 'username', 'smsCode']) {
  return collectErrors(
    accountSchema.safeParse({
      phone: phone.value,
      username: username.value,
      smsCode: smsCode.value,
    }),
    accountErrors.value,
    fields,
  )
}

function validatePassword(fields = ['password', 'confirmPassword', 'agreedToTerms']) {
  return collectErrors(
    passwordSchema.safeParse({
      password: password.value,
      confirmPassword: confirmPassword.value,
      agreedToTerms: agreedToTerms.value,
    }),
    passwordErrors.value,
    fields,
  )
}

function clearServerError(field: string) {
  delete serverErrors.value[field]
}

function fieldMessages(field: string) {
  return [accountErrors.value[field], passwordErrors.value[field], serverErrors.value[field]].filter(Boolean)
}

function startCountdown(seconds = 60) {
  countdown.value = seconds
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function sendSms() {
  errorMessage.value = ''
  if (!validateAccount(['phone'])) {
    toast.error(accountErrors.value.phone || '请先填写有效手机号')
    return
  }
  if (countdown.value > 0 || sendingSms.value) return

  sendingSms.value = true
  try {
    await authApi.sendSms(phone.value.trim(), 'REGISTER')
    startCountdown(60)
    toast.success('验证码已发送')
  } catch (error) {
    const message = error instanceof ApiRequestError
      ? error.message
      : '验证码发送失败，请稍后再试'
    errorMessage.value = message
    toast.error(message)
  } finally {
    sendingSms.value = false
  }
}

function continueToPassword() {
  errorMessage.value = ''
  if (!validateAccount()) return
  currentStep.value = 2
}

async function submit() {
  errorMessage.value = ''
  serverErrors.value = {}
  if (!validatePassword()) return

  submitting.value = true
  try {
    await auth.register({
      phone: phone.value.trim(),
      smsCode: smsCode.value.trim(),
      username: username.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value,
    })
    await auth.login({ identifier: username.value.trim(), password: password.value })
    await router.replace('/vault')
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      serverErrors.value = error.fieldErrors
      const passwordFields = ['password', 'confirmPassword']
      if (Object.keys(error.fieldErrors).some((field) => !passwordFields.includes(field))) {
        currentStep.value = 1
      }
      if (error.code === 'SMS_CODE_INVALID') {
        currentStep.value = 1
      }
    } else {
      errorMessage.value = '注册未完成，请稍后再试。'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell register>
    <div class="auth-form-heading auth-form-heading--register">
      <span class="auth-form-heading__eyebrow">创建你的空间</span>
      <h1>注册 Online Safe</h1>
      <p>两步完成：手机短信验证，再设置登录密码。</p>
    </div>

    <div class="auth-stepper" aria-label="注册进度">
      <div class="auth-stepper__label">
        <span>步骤 {{ currentStep }}/2</span>
        <strong>{{ stepTitle }}</strong>
      </div>
      <div class="auth-stepper__track">
        <span :style="{ width: `${(currentStep / 2) * 100}%` }" />
      </div>
    </div>

    <v-alert v-if="errorMessage" type="error" variant="tonal" density="comfortable" class="auth-form-alert" role="alert">
      {{ errorMessage }}
    </v-alert>

    <form v-if="currentStep === 1" class="auth-step-form" @submit.prevent="continueToPassword">
      <v-text-field
        v-model="phone"
        label="手机号"
        placeholder="请输入中国大陆手机号"
        prepend-inner-icon="mdi-cellphone"
        autocomplete="tel"
        inputmode="numeric"
        hint="将向该手机号发送短信验证码。"
        :error-messages="fieldMessages('phone')"
        @blur="validateAccount(['phone'])"
        @update:model-value="clearServerError('phone')"
      />
      <v-text-field
        v-model="username"
        class="mt-2"
        label="用户名"
        placeholder="设置你的登录用户名"
        prepend-inner-icon="mdi-account-outline"
        autocomplete="username"
        hint="3–32 位，可使用字母、数字、下划线和连字符。"
        :error-messages="fieldMessages('username')"
        @blur="validateAccount(['username'])"
        @update:model-value="clearServerError('username')"
      />
      <div class="auth-sms-row mt-2">
        <v-text-field
          v-model="smsCode"
          label="短信验证码"
          placeholder="请输入 6 位验证码"
          prepend-inner-icon="mdi-message-text-outline"
          autocomplete="one-time-code"
          inputmode="numeric"
          :error-messages="fieldMessages('smsCode')"
          @blur="validateAccount(['smsCode'])"
          @update:model-value="clearServerError('smsCode')"
        />
        <v-btn
          type="button"
          color="primary"
          variant="tonal"
          class="auth-sms-row__btn"
          :loading="sendingSms"
          :disabled="sendingSms || countdown > 0"
          @click="sendSms"
        >
          {{ sendButtonLabel }}
        </v-btn>
      </div>
      <v-btn type="submit" color="primary" block class="auth-submit">继续</v-btn>
    </form>

    <form v-else class="auth-step-form" @submit.prevent="submit">
      <v-text-field
        v-model="password"
        label="登录密码"
        placeholder="请输入至少 8 位密码"
        prepend-inner-icon="mdi-lock-outline"
        autocomplete="new-password"
        :type="showPassword ? 'text' : 'password'"
        :error-messages="fieldMessages('password')"
        @blur="validatePassword(['password'])"
        @update:model-value="clearServerError('password')"
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
      <ul class="password-rules" aria-label="密码规则">
        <li v-for="rule in passwordRules" :key="rule.label" :class="{ 'password-rules__item--passed': rule.passed }">
          <v-icon :icon="rule.passed ? 'mdi-check-circle' : 'mdi-circle-outline'" size="16" />
          {{ rule.label }}
        </li>
      </ul>
      <v-text-field
        v-model="confirmPassword"
        class="mt-3"
        label="确认密码"
        placeholder="请再次输入登录密码"
        prepend-inner-icon="mdi-lock-check-outline"
        autocomplete="new-password"
        :type="showConfirmPassword ? 'text' : 'password'"
        :error-messages="fieldMessages('confirmPassword')"
        @blur="validatePassword(['confirmPassword'])"
        @update:model-value="clearServerError('confirmPassword')"
      >
        <template #append-inner>
          <v-btn
            class="password-toggle"
            :icon="showConfirmPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
            variant="text"
            density="compact"
            :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'"
            @click="showConfirmPassword = !showConfirmPassword"
          />
        </template>
      </v-text-field>
      <div class="auth-terms" :class="{ 'auth-terms--error': fieldMessages('agreedToTerms').length }">
        <v-checkbox v-model="agreedToTerms" density="comfortable" hide-details @update:model-value="clearServerError('agreedToTerms')">
          <template #label>
            <span>我已阅读并同意<a href="#" @click.prevent>《服务条款》</a>及<a href="#" @click.prevent>《数据安全政策》</a></span>
          </template>
        </v-checkbox>
        <p v-if="fieldMessages('agreedToTerms').length" class="auth-field-error">{{ fieldMessages('agreedToTerms')[0] }}</p>
      </div>
      <div class="auth-form-actions">
        <v-btn variant="text" color="primary" :disabled="submitting" @click="currentStep = 1">上一步</v-btn>
        <v-btn type="submit" color="primary" :loading="submitting" :disabled="submitting">
          {{ submitting ? '正在创建…' : '创建账号' }}
        </v-btn>
      </div>
    </form>

    <p class="auth-alternate-action">已有账号？<router-link to="/login">去登录</router-link></p>
  </AuthShell>
</template>
