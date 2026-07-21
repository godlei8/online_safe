<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { z } from 'zod'
import AuthShell from '@/components/AuthShell.vue'
import { ApiRequestError } from '@/api/client'
import { authApi, type BuiltinSecurityQuestion, type SecurityQuestionPayload } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

type QuestionDraft = {
  mode: 'BUILTIN' | 'CUSTOM'
  questionCode: string
  questionText: string
  answer: string
}

const router = useRouter()
const auth = useAuthStore()
const currentStep = ref(1)
const phone = ref('')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const invitationCode = ref('')
const agreedToTerms = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const accountErrors = ref<Record<string, string>>({})
const passwordErrors = ref<Record<string, string>>({})
const securityErrors = ref<Record<string, string>>({})
const serverErrors = ref<Record<string, string>>({})
const builtins = ref<BuiltinSecurityQuestion[]>([])
const questions = ref<QuestionDraft[]>([
  { mode: 'BUILTIN', questionCode: '', questionText: '', answer: '' },
])

const accountSchema = z.object({
  phone: z.string().trim().regex(/^(?:\+86|86)?1[3-9]\d{9}$/, '请输入有效的中国大陆手机号'),
  username: z.string().trim().regex(/^[\p{L}\p{N}_-]{3,32}$/u, '用户名为 3–32 位字母、数字、下划线或连字符'),
  invitationCode: z.string().trim().min(1, '请输入邀请码').max(64, '邀请码格式不正确'),
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

const stepTitle = computed(() => {
  if (currentStep.value === 1) return '创建账号'
  if (currentStep.value === 2) return '设置密码'
  return '设置密保'
})

const builtinItems = computed(() =>
  builtins.value.map((item) => ({ title: item.text, value: item.code })),
)

onMounted(async () => {
  try {
    builtins.value = await authApi.listBuiltinSecurityQuestions()
    if (builtins.value[0] && !questions.value[0].questionCode) {
      questions.value[0].questionCode = builtins.value[0].code
    }
  } catch {
    errorMessage.value = '无法加载内置密保题库，仍可使用自定义问题。'
  }
})

function collectErrors(result: ReturnType<typeof accountSchema.safeParse> | ReturnType<typeof passwordSchema.safeParse>, target: Record<string, string>, fields: string[]) {
  for (const field of fields) delete target[field]
  if (!result.success) {
    for (const issue of result.error.issues) {
      const field = String(issue.path[0] ?? '')
      if (fields.includes(field) && !target[field]) target[field] = issue.message
    }
  }
  return result.success
}

function validateAccount(fields = ['phone', 'username', 'invitationCode']) {
  return collectErrors(accountSchema.safeParse({ phone: phone.value, username: username.value, invitationCode: invitationCode.value }), accountErrors.value, fields)
}

function validatePassword(fields = ['password', 'confirmPassword', 'agreedToTerms']) {
  return collectErrors(passwordSchema.safeParse({ password: password.value, confirmPassword: confirmPassword.value, agreedToTerms: agreedToTerms.value }), passwordErrors.value, fields)
}

function validateSecurity() {
  securityErrors.value = {}
  if (questions.value.length < 1 || questions.value.length > 3) {
    securityErrors.value.form = '密保问题数量须为 1 至 3 道'
    return false
  }
  const fingerprints = new Set<string>()
  for (let i = 0; i < questions.value.length; i++) {
    const q = questions.value[i]
    if (q.mode === 'BUILTIN') {
      if (!q.questionCode) {
        securityErrors.value[`q${i}`] = '请选择内置密保问题'
        return false
      }
      const fp = `BUILTIN:${q.questionCode}`
      if (fingerprints.has(fp)) {
        securityErrors.value.form = '密保问题不能重复'
        return false
      }
      fingerprints.add(fp)
    } else {
      const text = q.questionText.trim()
      if (text.length < 4 || text.length > 200) {
        securityErrors.value[`q${i}`] = '自定义问题长度为 4 至 200 字'
        return false
      }
      const fp = `CUSTOM:${text.toLowerCase()}`
      if (fingerprints.has(fp)) {
        securityErrors.value.form = '密保问题不能重复'
        return false
      }
      fingerprints.add(fp)
    }
    if (!q.answer.trim()) {
      securityErrors.value[`a${i}`] = '请填写密保答案'
      return false
    }
  }
  return true
}

function clearServerError(field: string) {
  delete serverErrors.value[field]
}

function fieldMessages(field: string) {
  return [accountErrors.value[field], passwordErrors.value[field], serverErrors.value[field]].filter(Boolean)
}

function continueToPassword() {
  errorMessage.value = ''
  if (!validateAccount()) return
  currentStep.value = 2
}

function continueToSecurity() {
  errorMessage.value = ''
  if (!validatePassword()) return
  currentStep.value = 3
}

function addQuestion() {
  if (questions.value.length >= 3) return
  questions.value.push({
    mode: 'BUILTIN',
    questionCode: builtins.value[0]?.code ?? '',
    questionText: '',
    answer: '',
  })
}

function removeQuestion(index: number) {
  if (questions.value.length <= 1) return
  questions.value.splice(index, 1)
}

function toPayload(): SecurityQuestionPayload[] {
  return questions.value.map((q) =>
    q.mode === 'BUILTIN'
      ? { questionType: 'BUILTIN', questionCode: q.questionCode, answer: q.answer }
      : { questionType: 'CUSTOM', questionText: q.questionText.trim(), answer: q.answer },
  )
}

async function submit() {
  errorMessage.value = ''
  serverErrors.value = {}
  if (!validateSecurity()) {
    errorMessage.value = securityErrors.value.form || Object.values(securityErrors.value)[0] || '请完善密保设置'
    return
  }

  submitting.value = true
  try {
    await auth.register({
      phone: phone.value.trim(),
      username: username.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value,
      invitationCode: invitationCode.value.trim(),
      securityQuestions: toPayload(),
    })
    await auth.login({ identifier: username.value.trim(), password: password.value })
    await router.replace('/vault')
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      serverErrors.value = error.fieldErrors
      const passwordFields = ['password', 'confirmPassword', 'securityQuestions']
      if (Object.keys(error.fieldErrors).some((field) => !passwordFields.includes(field) && !field.startsWith('securityQuestions'))) {
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
      <p>三步完成：账号、登录密码与密保问题。</p>
    </div>

    <div class="auth-stepper" aria-label="注册进度">
      <div class="auth-stepper__label">
        <span>步骤 {{ currentStep }}/3</span>
        <strong>{{ stepTitle }}</strong>
      </div>
      <div class="auth-stepper__track">
        <span :class="{ 'auth-stepper__progress--complete': currentStep > 1 }" />
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
        hint="仅支持中国大陆手机号，当前版本不进行短信验证。"
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
      <v-text-field
        v-model="invitationCode"
        class="mt-2"
        label="邀请码"
        placeholder="请输入邀请码"
        prepend-inner-icon="mdi-ticket-confirmation-outline"
        autocomplete="off"
        hint="邀请码由管理员或你的邀请渠道提供。"
        :error-messages="fieldMessages('invitationCode')"
        @blur="validateAccount(['invitationCode'])"
        @update:model-value="clearServerError('invitationCode')"
      />
      <v-btn type="submit" color="primary" block class="auth-submit">继续</v-btn>
    </form>

    <form v-else-if="currentStep === 2" class="auth-step-form" @submit.prevent="continueToSecurity">
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
        <v-btn variant="text" color="secondary" @click="currentStep = 1">上一步</v-btn>
        <v-btn type="submit" color="primary">继续</v-btn>
      </div>
    </form>

    <form v-else class="auth-step-form" @submit.prevent="submit">
      <p class="text-body-2 mb-4">请设置 1–3 道密保问题，用于忘记登录密码时重置。答案仅存哈希，不能用于解密保险箱。</p>

      <div v-for="(question, index) in questions" :key="index" class="mb-6">
        <div class="d-flex align-center justify-space-between mb-2">
          <strong>问题 {{ index + 1 }}</strong>
          <v-btn
            v-if="questions.length > 1"
            variant="text"
            color="secondary"
            size="small"
            @click="removeQuestion(index)"
          >
            移除
          </v-btn>
        </div>
        <v-btn-toggle v-model="question.mode" mandatory density="comfortable" class="mb-3" color="primary">
          <v-btn value="BUILTIN" size="small">内置题</v-btn>
          <v-btn value="CUSTOM" size="small">自定义</v-btn>
        </v-btn-toggle>
        <v-select
          v-if="question.mode === 'BUILTIN'"
          v-model="question.questionCode"
          :items="builtinItems"
          label="选择密保问题"
          :error-messages="securityErrors[`q${index}`]"
        />
        <v-text-field
          v-else
          v-model="question.questionText"
          label="自定义问题"
          placeholder="请输入你的密保问题"
          :error-messages="securityErrors[`q${index}`]"
        />
        <v-text-field
          v-model="question.answer"
          class="mt-2"
          label="答案"
          placeholder="请输入答案"
          autocomplete="off"
          :error-messages="securityErrors[`a${index}`]"
        />
      </div>

      <v-btn
        v-if="questions.length < 3"
        variant="tonal"
        color="primary"
        class="mb-4"
        prepend-icon="mdi-plus"
        @click="addQuestion"
      >
        再加一道（最多 3 道）
      </v-btn>

      <div class="auth-form-actions">
        <v-btn variant="text" color="secondary" :disabled="submitting" @click="currentStep = 2">上一步</v-btn>
        <v-btn type="submit" color="primary" :loading="submitting" :disabled="submitting">
          {{ submitting ? '正在创建…' : '创建账号' }}
        </v-btn>
      </div>
    </form>

    <p class="auth-alternate-action">已有账号？<router-link to="/login">去登录</router-link></p>
  </AuthShell>
</template>
