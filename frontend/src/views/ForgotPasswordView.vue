<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { z } from 'zod'
import AuthShell from '@/components/AuthShell.vue'
import { ApiRequestError } from '@/api/client'
import { authApi, type PasswordResetQuestion } from '@/api/auth'

const router = useRouter()
const step = ref(1)
const identifier = ref('')
const questions = ref<PasswordResetQuestion[]>([])
const answers = ref<string[]>([])
const newPassword = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

const stepLabel = computed(() => {
  if (step.value === 1) return '验证账号'
  if (step.value === 2) return '回答密保'
  return '设置新密码'
})

async function lookup() {
  errorMessage.value = ''
  fieldErrors.value = {}
  const parsed = z.string().trim().min(1, '请输入手机号或用户名').safeParse(identifier.value)
  if (!parsed.success) {
    fieldErrors.value.identifier = parsed.error.issues[0]?.message ?? '请输入账号'
    return
  }
  submitting.value = true
  try {
    const result = await authApi.lookupPasswordReset(parsed.data)
    questions.value = result.questions
    answers.value = result.questions.map(() => '')
    step.value = 2
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '无法完成验证，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}

function continueToPassword() {
  errorMessage.value = ''
  if (answers.value.some((item) => !item.trim())) {
    errorMessage.value = '请填写全部密保答案'
    return
  }
  step.value = 3
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
      identifier: identifier.value.trim(),
      answers: answers.value,
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
      if (error.code === 'PASSWORD_RESET_FAILED') step.value = 2
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
      <p>通过注册时设置的密保问题重置网站登录密码。本轮不支持短信或邮箱重置。</p>
    </div>

    <div class="auth-stepper" aria-label="重置进度">
      <div class="auth-stepper__label">
        <span>步骤 {{ step }}/3</span>
        <strong>{{ stepLabel }}</strong>
      </div>
      <div class="auth-stepper__track">
        <span :style="{ width: `${(step / 3) * 100}%` }" />
      </div>
    </div>

    <v-alert v-if="errorMessage" type="error" variant="tonal" density="comfortable" class="auth-form-alert" role="alert">
      {{ errorMessage }}
    </v-alert>

    <v-alert
      v-if="step === 3"
      type="info"
      variant="tonal"
      density="comfortable"
      class="auth-form-alert"
      role="status"
    >
      重置后请使用新密码登录；保险箱中的账密记录会保留。
    </v-alert>

    <form v-if="step === 1" class="auth-reset-form" @submit.prevent="lookup">
      <v-text-field
        v-model="identifier"
        label="手机号或用户名"
        placeholder="请输入注册时的手机号或用户名"
        prepend-inner-icon="mdi-account-outline"
        autocomplete="username"
        :error-messages="fieldErrors.identifier"
      />
      <v-btn type="submit" color="primary" block class="auth-submit" :loading="submitting" :disabled="submitting">
        继续
      </v-btn>
    </form>

    <form v-else-if="step === 2" class="auth-reset-form" @submit.prevent="continueToPassword">
      <div v-for="(question, index) in questions" :key="question.questionId" class="mb-4">
        <p class="text-body-2 mb-2">{{ index + 1 }}. {{ question.questionText }}</p>
        <v-text-field
          v-model="answers[index]"
          :label="`答案 ${index + 1}`"
          placeholder="请输入答案"
          autocomplete="off"
        />
      </div>
      <div class="auth-form-actions">
        <v-btn variant="text" color="primary" @click="step = 1">上一步</v-btn>
        <v-btn type="submit" color="primary">继续</v-btn>
      </div>
    </form>

    <form v-else class="auth-reset-form" @submit.prevent="confirm">
      <v-text-field
        v-model="newPassword"
        label="新登录密码"
        placeholder="至少 8 位"
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
            :aria-label="showPassword ? '隐藏新登录密码' : '显示新登录密码'"
            @click="showPassword = !showPassword"
          />
        </template>
      </v-text-field>
      <v-text-field
        v-model="confirmPassword"
        class="mt-2"
        label="确认新登录密码"
        placeholder="请再次输入"
        prepend-inner-icon="mdi-lock-check-outline"
        autocomplete="new-password"
        :type="showPassword ? 'text' : 'password'"
        :error-messages="fieldErrors.confirmPassword"
      />
      <div class="auth-form-actions">
        <v-btn variant="text" color="primary" :disabled="submitting" @click="step = 2">上一步</v-btn>
        <v-btn type="submit" color="primary" :loading="submitting" :disabled="submitting">重置密码</v-btn>
      </div>
    </form>

    <p class="auth-alternate-action"><router-link to="/login">返回登录</router-link></p>
  </AuthShell>
</template>
