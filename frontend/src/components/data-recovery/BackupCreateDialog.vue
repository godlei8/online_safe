<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { ApiRequestError } from '@/api/client'
import { dataSecurityApi } from '@/api/dataSecurity'
import { downloadOsVault, encryptBackupManifest } from '@/domain/vaultBackupCrypto'
import type { InnerManifest } from '@/domain/vaultBackupEnvelope'
import { useOsToast } from '@/composables/useOsToast'

const open = defineModel<boolean>({ required: true })
const emit = defineEmits<{ done: [] }>()

const { xs } = useDisplay()
const toast = useOsToast()

const step = ref<'reauth' | 'password' | 'working'>('reauth')
const loginPassword = ref('')
const backupPassword = ref('')
const confirmPassword = ref('')
const includeTrash = ref(true)
const busy = ref(false)
const primaryLabel = ref('验证身份')

watch(open, (value) => {
  if (!value) return
  step.value = 'reauth'
  loginPassword.value = ''
  backupPassword.value = ''
  confirmPassword.value = ''
  includeTrash.value = true
  busy.value = false
  primaryLabel.value = '验证身份'
})

const canContinue = computed(() => {
  if (busy.value) return false
  if (step.value === 'reauth') return loginPassword.value.length > 0
  if (step.value === 'password') {
    return backupPassword.value.length >= 12 && backupPassword.value === confirmPassword.value
  }
  return false
})

async function onPrimary() {
  if (!canContinue.value) return
  busy.value = true
  try {
    if (step.value === 'reauth') {
      primaryLabel.value = '验证身份'
      await dataSecurityApi.reauth(loginPassword.value)
      loginPassword.value = ''
      step.value = 'password'
      primaryLabel.value = '创建并下载'
      return
    }
    primaryLabel.value = '正在准备数据'
    const { snapshot } = await dataSecurityApi.createSnapshot(includeTrash.value)
    primaryLabel.value = '正在加密'
    const envelope = await encryptBackupManifest(
      { ...snapshot, schemaVersion: 1 as const } as InnerManifest,
      backupPassword.value,
    )
    primaryLabel.value = '下载备份'
    downloadOsVault(envelope)
    backupPassword.value = ''
    confirmPassword.value = ''
    toast.success('加密备份已开始下载')
    open.value = false
    emit('done')
  } catch (error) {
    toast.error(error instanceof ApiRequestError || error instanceof Error ? error.message : '创建备份失败')
    primaryLabel.value = step.value === 'reauth' ? '验证身份' : '创建并下载'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <v-dialog
    v-model="open"
    :fullscreen="xs"
    :max-width="xs ? undefined : 520"
    scrollable
    persistent
  >
    <v-card class="os-form-dialog">
      <v-card-title class="os-form-dialog__title">
        <div class="os-form-dialog__heading">
          <h2>创建加密备份</h2>
          <p class="text-medium-emphasis mb-0">
            备份文件由你设置的独立备份密码加密。系统不会保存该密码，遗失后无法找回。
          </p>
        </div>
        <v-btn icon="mdi-close" variant="text" aria-label="关闭" @click="open = false" />
      </v-card-title>
      <v-card-text>
        <template v-if="step === 'reauth'">
          <v-text-field
            v-model="loginPassword"
            type="password"
            label="当前登录密码"
            autocomplete="current-password"
            density="compact"
            hide-details="auto"
          />
        </template>
        <template v-else>
          <v-text-field
            v-model="backupPassword"
            type="password"
            label="备份密码（至少 12 位）"
            autocomplete="new-password"
            class="mb-3"
            density="compact"
            hide-details="auto"
          />
          <v-text-field
            v-model="confirmPassword"
            type="password"
            label="确认备份密码"
            autocomplete="new-password"
            class="mb-3"
            density="compact"
            hide-details="auto"
          />
          <v-checkbox
            v-model="includeTrash"
            label="包含回收站内容"
            density="compact"
            hide-details
          />
          <p class="text-caption text-medium-emphasis mt-2 mb-0">
            Markdown 明文导出仍可在保险箱首页使用；本处为可恢复的加密备份（.osvault）。
          </p>
        </template>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" :disabled="busy" @click="open = false">取消</v-btn>
        <v-btn color="primary" :loading="busy" :disabled="!canContinue" @click="onPrimary">
          {{ primaryLabel }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
