<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ApiRequestError } from '@/api/client'
import {
  systemSettingsApi,
  type SettingChange,
  type SystemSettingItem,
  type SystemSettingsResponse,
  type ValidateResponse,
} from '@/api/systemSettings'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'
import { useOsToast } from '@/composables/useOsToast'

type SettingGroup = 'ACCOUNT' | 'INVITATION' | 'SECURITY_LOG'

const GROUP_META: Record<SettingGroup, { title: string; subtitle: string }> = {
  ACCOUNT: { title: '注册与账号', subtitle: '注册模式、密码规则与用户名冷却期' },
  INVITATION: { title: '邀请码默认值', subtitle: '仅影响新建邀请码表单' },
  SECURITY_LOG: { title: '安全日志', subtitle: '下次清理任务时生效' },
}

const MODE_LABELS: Record<string, string> = {
  CLOSED: '关闭注册',
  SMS_VERIFIED: '短信验证',
  INVITE_AND_SMS: '邀请码+短信',
}

const RETENTION_LABELS: Record<number, string> = {
  90: '90 天',
  180: '180 天',
  365: '365 天',
}

const loading = ref(false)
const savingGroup = ref<SettingGroup | null>(null)
const settings = ref<SystemSettingsResponse | null>(null)
const drafts = reactive<Record<string, unknown>>({})
const originals = reactive<Record<string, { value: unknown; version: number }>>({})

const saveConfirmOpen = ref(false)
const leaveConfirmOpen = ref(false)
const confirmLoading = ref(false)
const pendingSaveGroup = ref<SettingGroup | null>(null)
const pendingValidate = ref<ValidateResponse | null>(null)
const pendingChanges = ref<SettingChange[]>([])

let leaveResolver: ((value: boolean) => void) | null = null

const toast = useOsToast()

const hasAnyDirty = computed(() => Object.keys(originals).some((key) => isDirty(key)))

onMounted(() => {
  void loadSettings()
})

onBeforeRouteLeave(() => {
  if (!hasAnyDirty.value) return true
  leaveConfirmOpen.value = true
  return new Promise<boolean>((resolve) => {
    leaveResolver = resolve
  })
})

async function loadSettings() {
  loading.value = true
  try {
    const result = await systemSettingsApi.get()
    applySettings(result)
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '加载系统设置失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}

function applySettings(result: SystemSettingsResponse) {
  settings.value = result
  for (const key of Object.keys(drafts)) delete drafts[key]
  for (const key of Object.keys(originals)) delete originals[key]
  for (const items of Object.values(result.groups)) {
    for (const item of items) {
      originals[item.key] = { value: cloneValue(item.value), version: item.version }
      drafts[item.key] = cloneValue(item.value)
    }
  }
}

function cloneValue(value: unknown) {
  if (typeof value === 'number' || typeof value === 'string' || typeof value === 'boolean') return value
  return value
}

function settingByKey(key: string): SystemSettingItem | undefined {
  if (!settings.value) return undefined
  for (const items of Object.values(settings.value.groups)) {
    const found = items.find((item) => item.key === key)
    if (found) return found
  }
  return undefined
}

function groupItems(group: SettingGroup): SystemSettingItem[] {
  return settings.value?.groups[group] ?? []
}

function isDirty(key: string) {
  const original = originals[key]
  if (!original) return false
  return !valuesEqual(drafts[key], original.value)
}

function valuesEqual(a: unknown, b: unknown) {
  if (typeof a === 'number' || typeof b === 'number') return Number(a) === Number(b)
  return String(a) === String(b)
}

function groupDirty(group: SettingGroup) {
  return groupItems(group).some((item) => item.editable && isDirty(item.key))
}

function modeOptions(item: SystemSettingItem) {
  return item.allowedValues.map((value) => ({
    title: MODE_LABELS[value] ?? value,
    value,
  }))
}

function retentionOptions(item: SystemSettingItem) {
  return item.allowedValues.map((value) => {
    const num = Number(value)
    return {
      title: RETENTION_LABELS[num] ?? `${value} 天`,
      value: num,
    }
  })
}

const CAPABILITY_ICONS: Record<string, string> = {
  sms: 'mdi-message-text-outline',
  avatarStorage: 'mdi-cloud-upload-outline',
  adminMfa: 'mdi-two-factor-authentication',
  secureCookie: 'mdi-cookie-outline',
  sessionTimeout: 'mdi-timer-outline',
  auditWrite: 'mdi-shield-check-outline',
}

function capabilityEntries() {
  if (!settings.value) return []
  return Object.entries(settings.value.capabilities).map(([key, item]) => ({
    key,
    icon: CAPABILITY_ICONS[key] ?? 'mdi-information-outline',
    ...item,
  }))
}

function capabilityColor(status: string) {
  if (['AVAILABLE', 'OK', 'TENCENT_COS', 'HTTPS_REQUIRED'].includes(status)) return 'success'
  if (['NOT_CONFIGURED', 'NOT_IMPLEMENTED'].includes(status)) return 'warning'
  return 'info'
}

const editableGroups: SettingGroup[] = ['ACCOUNT', 'INVITATION', 'SECURITY_LOG']

function rangeHint(item: SystemSettingItem) {
  if (item.min == null || item.max == null) return undefined
  return `${item.min}–${item.max}`
}

function buildGroupChanges(group: SettingGroup): SettingChange[] {
  return groupItems(group)
    .filter((item) => item.editable && isDirty(item.key))
    .map((item) => ({
      key: item.key,
      value: drafts[item.key],
      expectedVersion: originals[item.key]?.version ?? item.version,
    }))
}

function needsConfirm(validate: ValidateResponse, changes: SettingChange[]) {
  if (validate.highestRisk === 'HIGH') return true
  return changes.some((change) => {
    if (change.key === 'registration.mode') {
      return change.value === 'CLOSED' || change.value === 'INVITE_AND_SMS'
    }
    if (change.key === 'security.audit_retention_days') {
      const current = Number(originals[change.key]?.value ?? settingByKey(change.key)?.value)
      return Number(change.value) < current
    }
    return false
  })
}

function confirmMessageFromValidate(validate: ValidateResponse) {
  return validate.effects.map((line) => `· ${line}`).join('\n')
}

async function saveGroup(group: SettingGroup) {
  const changes = buildGroupChanges(group)
  if (changes.length === 0) {
    toast.warning('当前分组没有待保存的修改')
    return
  }

  savingGroup.value = group
  try {
    const validate = await systemSettingsApi.validate(changes)
    if (!validate.valid) {
      toast.error('设置校验未通过，请检查输入后重试')
      return
    }
    if (needsConfirm(validate, changes)) {
      pendingSaveGroup.value = group
      pendingValidate.value = validate
      pendingChanges.value = changes
      saveConfirmOpen.value = true
      return
    }
    await performSave(changes)
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。')
  } finally {
    savingGroup.value = null
  }
}

async function confirmSave() {
  if (!pendingSaveGroup.value || pendingChanges.value.length === 0) return
  confirmLoading.value = true
  savingGroup.value = pendingSaveGroup.value
  try {
    await performSave(pendingChanges.value)
    saveConfirmOpen.value = false
    pendingSaveGroup.value = null
    pendingValidate.value = null
    pendingChanges.value = []
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。')
  } finally {
    confirmLoading.value = false
    savingGroup.value = null
  }
}

async function performSave(changes: SettingChange[]) {
  const result = await systemSettingsApi.update(changes)
  applySettings(result)
  toast.success('系统设置已更新')
}

function cancelSaveConfirm() {
  if (confirmLoading.value) return
  saveConfirmOpen.value = false
  pendingSaveGroup.value = null
  pendingValidate.value = null
  pendingChanges.value = []
}

function confirmLeave() {
  leaveConfirmOpen.value = false
  leaveResolver?.(true)
  leaveResolver = null
}

function cancelLeave() {
  leaveConfirmOpen.value = false
  leaveResolver?.(false)
  leaveResolver = null
}

const saveConfirmTitle = computed(() => pendingValidate.value?.confirmationTitle ?? '确认保存系统设置？')
const saveConfirmMessage = computed(() => {
  if (!pendingValidate.value) return '是否继续保存？'
  return confirmMessageFromValidate(pendingValidate.value)
})
const saveConfirmVariant = computed(() => (
  pendingValidate.value?.highestRisk === 'HIGH' ? 'danger' : 'warning'
))
</script>

<template>
  <div class="admin-settings">
    <div class="admin-settings__toolbar">
      <p class="admin-settings__hint text-medium-emphasis mb-0">
        各分组独立保存；高风险变更需二次确认。
      </p>
      <v-btn
        class="admin-toolbar-btn admin-settings__refresh"
        variant="tonal"
        color="primary"
        prepend-icon="mdi-refresh"
        :loading="loading"
        @click="loadSettings"
      >
        刷新
      </v-btn>
    </div>

    <v-skeleton-loader v-if="loading && !settings" type="article@2" />

    <template v-else-if="settings">
      <v-card class="admin-panel admin-settings__capabilities" elevation="0">
        <div class="admin-settings__cap-head">
          <div>
            <h2 class="admin-settings__section-title">运行能力</h2>
            <p class="admin-settings__section-subtitle text-medium-emphasis mb-0">
              只读状态总览，由部署配置决定，不可在线修改
            </p>
          </div>
        </div>
        <div class="admin-settings__cap-grid">
          <div
            v-for="item in capabilityEntries()"
            :key="item.key"
            class="admin-settings__cap-card"
            :data-tone="capabilityColor(item.status)"
          >
            <div class="admin-settings__cap-icon" aria-hidden="true">
              <v-icon :icon="item.icon" size="20" />
            </div>
            <div class="admin-settings__cap-body">
              <div class="admin-settings__cap-label">{{ item.label }}</div>
              <div class="admin-settings__cap-status">{{ item.statusLabel }}</div>
            </div>
          </div>
        </div>
      </v-card>

      <div class="admin-settings__groups">
        <v-card
          v-for="group in editableGroups"
          :key="group"
          class="admin-panel admin-settings__group"
          elevation="0"
        >
          <div class="admin-settings__group-head">
            <div class="admin-settings__group-meta">
              <h2 class="admin-settings__group-title">{{ GROUP_META[group].title }}</h2>
              <p class="admin-settings__group-subtitle text-medium-emphasis mb-0">
                {{ GROUP_META[group].subtitle }}
                <span v-if="groupDirty(group)" class="admin-settings__dirty">· 未保存</span>
              </p>
            </div>
            <v-btn
              class="admin-toolbar-btn admin-settings__save"
              :variant="groupDirty(group) ? 'flat' : 'tonal'"
              color="primary"
              prepend-icon="mdi-content-save-outline"
              :disabled="!groupDirty(group) || savingGroup !== null"
              :loading="savingGroup === group"
              @click="saveGroup(group)"
            >
              保存
            </v-btn>
          </div>

          <div class="admin-settings__fields">
            <template v-for="item in groupItems(group)" :key="item.key">
              <v-select
                v-if="item.key === 'registration.mode'"
                v-model="drafts[item.key]"
                class="admin-settings__field"
                :items="modeOptions(item)"
                item-title="title"
                item-value="value"
                :label="item.labelZh"
                :disabled="!item.editable || savingGroup !== null"
                density="compact"
                hide-details
              />
              <v-select
                v-else-if="item.key === 'security.audit_retention_days'"
                v-model="drafts[item.key]"
                class="admin-settings__field"
                :items="retentionOptions(item)"
                item-title="title"
                item-value="value"
                :label="item.labelZh"
                :disabled="!item.editable || savingGroup !== null"
                density="compact"
                hide-details
              />
              <v-text-field
                v-else
                v-model.number="drafts[item.key]"
                class="admin-settings__field"
                type="number"
                :label="item.labelZh"
                :min="item.min ?? undefined"
                :max="item.max ?? undefined"
                :suffix="rangeHint(item)"
                :disabled="!item.editable || savingGroup !== null"
                density="compact"
                hide-details
              />
            </template>
          </div>
        </v-card>
      </div>
    </template>

    <OsConfirmDialog
      v-model="saveConfirmOpen"
      :title="saveConfirmTitle"
      :message="saveConfirmMessage"
      confirm-text="确认保存"
      :variant="saveConfirmVariant"
      :loading="confirmLoading"
      @confirm="confirmSave"
      @cancel="cancelSaveConfirm"
    />

    <OsConfirmDialog
      v-model="leaveConfirmOpen"
      title="放弃未保存的修改？"
      message="当前页面有尚未保存的系统设置，离开后将丢失这些修改。"
      confirm-text="离开页面"
      cancel-text="继续编辑"
      variant="warning"
      @confirm="confirmLeave"
      @cancel="cancelLeave"
    />
  </div>
</template>

<style scoped>
.admin-settings {
  display: grid;
  gap: 8px;
}

.admin-settings__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 30px;
  margin-bottom: 2px;
}

.admin-settings__hint {
  font-size: 0.71875rem;
  line-height: 1.35;
}

.admin-settings__section-title,
.admin-settings__group-title {
  margin: 0;
  color: var(--os-text-title);
  font-size: 0.875rem;
  font-weight: 650;
  line-height: 1.25;
}

.admin-settings__section-subtitle,
.admin-settings__group-subtitle {
  margin-top: 1px;
  font-size: 0.71875rem;
  line-height: 1.3;
}

.admin-settings__capabilities {
  padding: 14px 16px !important;
}

.admin-settings__cap-head {
  margin-bottom: 10px;
}

.admin-settings__cap-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.admin-settings__cap-card {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  column-gap: 12px;
  align-items: center;
  min-height: 78px;
  padding: 14px 16px;
  border: 1px solid var(--os-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--os-surface) 94%, var(--os-bg));
}

.admin-settings__cap-icon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 8px;
  color: var(--os-primary);
  background: var(--os-primary-tint);
}

.admin-settings__cap-card[data-tone='success'] .admin-settings__cap-icon {
  color: rgb(var(--v-theme-success));
  background: rgba(var(--v-theme-success), 0.12);
}

.admin-settings__cap-card[data-tone='warning'] .admin-settings__cap-icon {
  color: rgb(var(--v-theme-warning));
  background: rgba(var(--v-theme-warning), 0.14);
}

.admin-settings__cap-card[data-tone='info'] .admin-settings__cap-icon {
  color: rgb(var(--v-theme-info));
  background: rgba(var(--v-theme-info), 0.12);
}

.admin-settings__cap-body {
  min-width: 0;
}

.admin-settings__cap-label {
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.3;
}

.admin-settings__cap-status {
  margin-top: 4px;
  color: var(--os-text-title);
  font-size: 0.875rem;
  font-weight: 650;
  line-height: 1.35;
  word-break: break-word;
}

.admin-settings__groups {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  align-items: stretch;
}

.admin-settings__group {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 14px !important;
}

.admin-settings__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.admin-settings__group-meta {
  min-width: 0;
}

.admin-settings__dirty {
  color: rgb(var(--v-theme-warning));
  font-weight: 600;
}

.admin-settings__save,
.admin-settings__refresh {
  flex: 0 0 auto;
  min-width: 64px !important;
  padding-inline: 8px !important;
}

.admin-settings__fields {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  align-content: start;
}

.admin-settings__field {
  max-width: none;
}

.admin-settings__field :deep(.v-field) {
  --v-field-padding-start: 10px;
  --v-field-padding-end: 10px;
  font-size: 0.8125rem;
}

.admin-settings__field :deep(.v-label) {
  font-size: 0.75rem;
}

.admin-settings__field :deep(.v-text-field__suffix) {
  font-size: 0.6875rem;
  opacity: 0.62;
  padding-inline-start: 4px;
}

@media (max-width: 1100px) {
  .admin-settings__cap-grid,
  .admin-settings__groups {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .admin-settings__toolbar {
    align-items: flex-start;
  }

  .admin-settings__cap-grid,
  .admin-settings__groups {
    grid-template-columns: 1fr;
  }

  .admin-settings__cap-card {
    min-height: 70px;
  }
}
</style>
