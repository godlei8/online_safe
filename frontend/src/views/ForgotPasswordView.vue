<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { z } from 'zod'
import AuthShell from '@/components/AuthShell.vue'
import { ApiRequestError } from '@/api/client'
import { authApi } from '@/api/auth'

const router = useRouter()
const step = ref(1)
const phone = ref('')
const smsCode = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const sendingSms = ref(false)
const countdown = ref(0)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

let countdownTimer: ReturnType<typeof setInterval> | null = null

const stepLabel = computed(() => (step.value === 1 ? '验证手机号' : '设置新密码'))

const sendButtonLabel = computed(() => {
  if (sendingSms.value) return '发送中…'
  if (countdown.value > 0) return `${countdown.value}s 后可重发`
  return '获取验证码'
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

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
  fieldErrors.value = {}
  const parsed = z.string().trim().regex(/^(?:\+86|86)?1[3-9]\d{9}$/, '请输入有效的中国大陆手机号').safeParse(phone.value)
  if (!parsed.success) {
    fieldErrors.value.phone = parsed.error.issues[0]?.message ?? '请输入手机号'
    return
  }
  if (countdown.value > 0 || sendingSms.value) return

  sendingSms.value = true
  try {
    await authApi.sendSms(parsed.data, 'RESET_PASSWORD')
    startCountdown(60)
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '验证码发送失败，请稍后再试'
    }
  } finally {
    sendingSms.value = false
  }
}

function continueToPassword() {
  errorMessage.value = ''
  fieldErrors.value = {}
  const phoneParsed = z.string().trim().regex(/^(?:\+86|86)?1[3-9]\d{9}$/, '请输入有效的中国大陆手机号').safeParse(phone.value)
  if (!phoneParsed.success) {
    fieldErrors.value.phone = phoneParsed.error.issues[0]?.message ?? '请输入手机号'
    return
  }
  const codeParsed = z.string().trim().regex(/^\d{4,8}$/, '请输入短信验证码').safeParse(smsCode.value)
  if (!codeParsed.success) {
    fieldErrors.value.smsCode = codeParsed.error.issues[0]?.message ?? '请输入验证码'
    return
  }
  step.value = 2
}

async function confirm() {
  errorMessage.value = ''
  fieldErrors.value = {}
  if (newPassword.value.length < 8) {
    fieldErrors.value.newPassword = '新登录密码至少 8 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    fieldErrors.value.confirmPassword = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    await authApi.confirmPasswordReset({
      phone: phone.value.trim(),
      smsCode: smsCode.value.trim(),
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value,
    })
    await router.replace({
      path: '/login',
      query: { reset: '1' },
    })
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      if (error.code === 'PASSWORD_RESET_FAILED' || error.code === 'SMS_CODE_INVALID') {
        step.value = 1
      }
    } else {
      errorMessage.value = '重置未完成，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell>
    <div class="auth-form-heading">
      <span class="auth-form-heading__eyebrow">账号安全</span>
      <h1>忘记登录密码</h1>
      <p>通过已注册手机号收取短信验证码后设置新密码。若手机号无法使用，请联系管理员。</p>
    </div>

    <div class="auth-stepper" aria-label="重置进度">
      <div class="auth-stepper__label">
        <span>步骤 {{ step }}/2</span>
        <strong>{{ stepLabel }}</strong>
      </div>
      <div class="auth-stepper__track">
        <span :style="{ width: `${(step / 2) * 100}%` }" />
      </div>
    </div>

    <v-alert v-if="errorMessage" type="error" variant="tonal" density="comfortable" class="auth-form-alert" role="alert">
      {{ errorMessage }}
    </v-alert>

    <v-alert
      v-if="step === 2"
      type="info"
      variant="tonal"
      density="comfortable"
      class="auth-form-alert"
      role="status"
    >
      重置后请使用新密码登录；保险箱中的账密记录会保留。
    </v-alert>

    <form v-if="step === 1" class="auth-reset-form" @submit.prevent="continueToPassword">
      <v-text-field
        v-model="phone"
        label="手机号"
        placeholder="请输入注册时的手机号"
        prepend-inner-icon="mdi-cellphone"
        autocomplete="tel"
        inputmode="numeric"
        :error-messages="fieldErrors.phone"
      />
      <div class="auth-sms-row">
        <v-text-field
          v-model="smsCode"
          label="短信验证码"
          placeholder="请输入 6 位验证码"
          prepend-inner-icon="mdi-message-text-outline"
          autocomplete="one-time-code"
          inputmode="numeric"
          :error-messages="fieldErrors.smsCode"
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
      <v-btn type="submit" color="primary" block class="auth-submit" :disabled="submitting">
        继续
      </v-btn>
    </form>

    <form v-else class="auth-reset-form" @submit.prevent="confirm">
      <v-text-field
        v-model="newPassword"
        label="新登录密码"
        placeholder="请输入至少 8 位密码"
        prepend-inner-icon="mdi-lock-outline"
        autocomplete="new-password"
        :type="showPassword ? 'text' : 'password'"
        :error-messages="fieldErrors.newPassword"
      >
        <template #append-inner>
          <v-btn
            class="password-toggle"
            :icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
            variant="text"
            density="compact"
            :aria-label="showPassword ? '隐藏新密码' : '显示新密码'"
            @click="showPassword = !showPassword"
          />
        </template>
      </v-text-field>
      <v-text-field
        v-model="confirmPassword"
        class="mt-2"
        label="确认新密码"
        placeholder="请再次输入新密码"
        prepend-inner-icon="mdi-lock-check-outline"
        autocomplete="new-password"
        :type="showPassword ? 'text' : 'password'"
        :error-messages="fieldErrors.confirmPassword"
      />
      <div class="auth-form-actions">
        <v-btn variant="text" color="primary" :disabled="submitting" @click="step = 1">上一步</v-btn>
        <v-btn type="submit" color="primary" :loading="submitting" :disabled="submitting">
          重置密码
        </v-btn>
      </div>
    </form>

    <p class="auth-alternate-action">想起密码了？<router-link to="/login">返回登录</router-link></p>
  </AuthShell>
</template>
