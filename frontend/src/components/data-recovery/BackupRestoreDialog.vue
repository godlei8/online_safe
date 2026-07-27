<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { ApiRequestError } from '@/api/client'
import { dataSecurityApi, type RestoreBatchEntry } from '@/api/dataSecurity'
import { BACKUP_PASSWORD_ERROR, decryptBackupFile } from '@/domain/vaultBackupCrypto'
import type { InnerManifest } from '@/domain/vaultBackupEnvelope'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'
import { useOsToast } from '@/composables/useOsToast'

const open = defineModel<boolean>({ required: true })
const emit = defineEmits<{ done: [] }>()

const { xs } = useDisplay()
const toast = useOsToast()

type Step = 'file' | 'unlock' | 'preview' | 'merge'
const step = ref<Step>('file')
const file = ref<File | null>(null)
const backupPassword = ref('')
const loginPassword = ref('')
const includeTrashAssets = ref(false)
const manifest = ref<InnerManifest | null>(null)
const busy = ref(false)
const progress = ref(0)
const confirmOpen = ref(false)

const preview = ref({
  createCount: 0,
  restoreDeletedCount: 0,
  skipActiveConflictCount: 0,
  reassignIdCount: 0,
  invalidCount: 0,
  trashInBackupCount: 0,
})

watch(open, (value) => {
  if (!value) return
  step.value = 'file'
  file.value = null
  backupPassword.value = ''
  loginPassword.value = ''
  includeTrashAssets.value = false
  manifest.value = null
  busy.value = false
  progress.value = 0
})

const primaryLabel = computed(() => {
  if (step.value === 'file') return '下一步'
  if (step.value === 'unlock') return '解锁并校验'
  if (step.value === 'preview') return '开始安全合并'
  return '合并中…'
})

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
}

async function onPrimary() {
  if (busy.value) return
  if (step.value === 'file') {
    if (!file.value) {
      toast.error('请选择 .osvault 备份文件')
      return
    }
    step.value = 'unlock'
    return
  }
  if (step.value === 'unlock') {
    if (!file.value || !backupPassword.value) {
      toast.error('请输入备份密码')
      return
    }
    busy.value = true
    try {
      const unlocked = await decryptBackupFile(file.value, backupPassword.value)
      manifest.value = unlocked
      backupPassword.value = ''
      const entries = [
        ...unlocked.items.map((item) => ({
          type: 'ITEM',
          sourceId: item.id,
          deletedInBackup: !!item.deletedAt,
        })),
        ...unlocked.privateTemplates.map((item) => ({
          type: 'PRIVATE_TEMPLATE',
          sourceId: item.id,
          deletedInBackup: !!item.deletedAt,
        })),
      ]
      const result = await dataSecurityApi.preflight({
        sourceBackupId: unlocked.backupId,
        formatVersion: unlocked.schemaVersion,
        entries,
      })
      preview.value = {
        createCount: result.createCount,
        restoreDeletedCount: result.restoreDeletedCount,
        skipActiveConflictCount: result.skipActiveConflictCount,
        reassignIdCount: result.reassignIdCount,
        invalidCount: result.invalidCount,
        trashInBackupCount: result.trashInBackupCount,
      }
      step.value = 'preview'
    } catch (error) {
      toast.error(error instanceof Error ? error.message : BACKUP_PASSWORD_ERROR)
    } finally {
      busy.value = false
    }
    return
  }
  if (step.value === 'preview') {
    confirmOpen.value = true
  }
}

async function runMerge() {
  if (!manifest.value || busy.value) return
  busy.value = true
  step.value = 'merge'
  progress.value = 0
  try {
    if (!loginPassword.value) {
      toast.error('请输入当前登录密码以验证身份')
      step.value = 'preview'
      return
    }
    await dataSecurityApi.reauth(loginPassword.value)
    loginPassword.value = ''

    const activeEntries: RestoreBatchEntry[] = []
    for (const item of manifest.value.items) {
      if (item.deletedAt && !includeTrashAssets.value) continue
      activeEntries.push({
        type: 'ITEM',
        sourceId: item.id,
        payload: item.payload,
        deletedInBackup: !!item.deletedAt,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      })
    }
    for (const item of manifest.value.privateTemplates) {
      if (item.deletedAt && !includeTrashAssets.value) continue
      activeEntries.push({
        type: 'PRIVATE_TEMPLATE',
        sourceId: item.id,
        payload: item.payload,
        deletedInBackup: !!item.deletedAt,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      })
    }

    const operation = await dataSecurityApi.createRestoreOperation({
      sourceBackupId: manifest.value.backupId,
      formatVersion: manifest.value.schemaVersion,
      totalCount: activeEntries.length,
      includeTrashAssets: includeTrashAssets.value,
    })

    const size = 100
    let batchNo = 1
    for (let i = 0; i < activeEntries.length; i += size) {
      const chunk = activeEntries.slice(i, i + size)
      await dataSecurityApi.submitRestoreBatch(operation.operationId, batchNo, {
        includeTrashAssets: includeTrashAssets.value,
        entries: chunk,
      })
      progress.value = Math.round(((i + chunk.length) / Math.max(activeEntries.length, 1)) * 100)
      batchNo += 1
    }
    const result = await dataSecurityApi.completeRestore(operation.operationId)
    toast.success(
      `恢复完成：新建 ${result.createdCount}，恢复 ${result.restoredCount}，跳过 ${result.skippedCount}`,
    )
    manifest.value = null
    open.value = false
    emit('done')
  } catch (error) {
    toast.error(error instanceof ApiRequestError || error instanceof Error ? error.message : '数据恢复未完成')
    step.value = 'preview'
  } finally {
    busy.value = false
    confirmOpen.value = false
  }
}
</script>

<template>
  <v-dialog
    v-model="open"
    :fullscreen="xs"
    :max-width="xs ? undefined : 560"
    scrollable
    persistent
  >
    <v-card class="os-form-dialog">
      <v-card-title class="os-form-dialog__title">
        <div class="os-form-dialog__heading">
          <h2>从备份恢复</h2>
          <p class="text-medium-emphasis mb-0">仅安全合并，不会覆盖当前已有活跃记录。</p>
        </div>
        <v-btn icon="mdi-close" variant="text" aria-label="关闭" :disabled="busy" @click="open = false" />
      </v-card-title>
      <v-card-text>
        <template v-if="step === 'file'">
          <input type="file" accept=".osvault,application/json" @change="onFileChange" />
          <p class="text-caption text-medium-emphasis mt-2 mb-0">仅支持 .osvault 加密备份，不支持从 Markdown 恢复。</p>
        </template>
        <template v-else-if="step === 'unlock'">
          <v-text-field
            v-model="backupPassword"
            type="password"
            label="备份密码"
            density="compact"
            hide-details="auto"
          />
        </template>
        <template v-else>
          <div class="restore-preview">
            <p>将新建：{{ preview.createCount + preview.reassignIdCount }}</p>
            <p>将恢复软删除：{{ preview.restoreDeletedCount }}</p>
            <p>将跳过冲突：{{ preview.skipActiveConflictCount }}</p>
            <p>无效：{{ preview.invalidCount }}</p>
            <p>备份中的回收站资产：{{ preview.trashInBackupCount }}</p>
          </div>
          <v-checkbox
            v-model="includeTrashAssets"
            label="同时恢复备份中的回收站资产"
            density="compact"
            hide-details
            class="mb-3"
          />
          <v-text-field
            v-model="loginPassword"
            type="password"
            label="当前登录密码（二次验证）"
            density="compact"
            hide-details="auto"
          />
          <v-progress-linear
            v-if="step === 'merge'"
            class="mt-4"
            :model-value="progress"
            color="primary"
            height="6"
            rounded
          />
        </template>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" :disabled="busy" @click="open = false">取消</v-btn>
        <v-btn color="primary" :loading="busy" :disabled="step === 'merge'" @click="onPrimary">
          {{ primaryLabel }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <OsConfirmDialog
    v-model="confirmOpen"
    title="确认安全合并？"
    message="将按预览结果合并备份数据，不会覆盖当前活跃记录。是否继续？"
    confirm-text="开始合并"
    variant="warning"
    :loading="busy"
    @confirm="runMerge"
  />
</template>

<style scoped>
.restore-preview {
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
  font-size: 0.875rem;
}
</style>
