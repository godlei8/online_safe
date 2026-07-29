<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { adminAiApi, type AiConnectivityTestResponse } from '@/api/adminAi'
import { ApiRequestError } from '@/api/client'
import {
  systemSettingsApi,
  type SettingChange,
  type SystemSettingItem,
  type SystemSettingsResponse,
} from '@/api/systemSettings'
import OsConfirmDialog from '@/components/OsConfirmDialog.vue'
import { useOsToast } from '@/composables/useOsToast'

const SECRET_MASK = '********'

type FieldType = 'BOOLEAN' | 'INTEGER' | 'STRING' | 'SECRET'

type FieldDef = {
  key: string
  labelZh: string
  type: FieldType
  defaultValue: unknown
  min?: number
  max?: number
  hint?: string
}

const toast = useOsToast()
const loading = ref(false)
const saving = ref(false)
const settings = ref<SystemSettingsResponse | null>(null)
const drafts = reactive<Record<string, unknown>>({})
const originals = reactive<Record<string, unknown>>({})
const versions = reactive<Record<string, number>>({})
const configuredFlags = reactive<Record<string, boolean>>({})
const confirmOpen = ref(false)
const confirmLoading = ref(false)
const pendingChanges = ref<SettingChange[]>([])
const loadError = ref('')
const backendReady = ref(false)
const testing = ref(false)
const testResult = ref<AiConnectivityTestResponse | null>(null)

const IMPORT_FIELDS: FieldDef[] = [
  {
    key: 'feature.vault_smart_import_enabled',
    labelZh: '启用智能导入',
    type: 'BOOLEAN',
    defaultValue: true,
  },
  {
    key: 'feature.vault_smart_import_confidence_threshold',
    labelZh: '智能导入置信度阈值（%）',
    type: 'INTEGER',
    defaultValue: 75,
    min: 50,
    max: 95,
  },
  {
    key: 'feature.vault_smart_import_max_file_bytes',
    labelZh: '智能导入单文件大小上限（字节）',
    type: 'INTEGER',
    defaultValue: 5_242_880,
    min: 1_048_576,
    max: 10_485_760,
    hint: '默认 5MB（5242880）',
  },
  {
    key: 'feature.vault_smart_import_max_rows',
    labelZh: '智能导入单次最大条数',
    type: 'INTEGER',
    defaultValue: 200,
    min: 10,
    max: 500,
  },
  {
    key: 'feature.vault_smart_import_session_ttl_minutes',
    labelZh: '智能导入会话有效分钟数',
    type: 'INTEGER',
    defaultValue: 30,
    min: 10,
    max: 120,
  },
]

const MODEL_FIELDS: FieldDef[] = [
  {
    key: 'ai.model.enabled',
    labelZh: '启用大模型调用',
    type: 'BOOLEAN',
    defaultValue: false,
  },
  {
    key: 'ai.model.base_url',
    labelZh: '模型 API 根地址（OpenAI 兼容，含 /v1）',
    type: 'STRING',
    defaultValue: '',
    hint: 'DeepSeek 填 https://api.deepseek.com 或 …/v1；勿带 /chat/completions',
  },
  {
    key: 'ai.model.name',
    labelZh: '模型名称',
    type: 'STRING',
    defaultValue: '',
  },
  {
    key: 'ai.model.api_key',
    labelZh: '模型 API Key',
    type: 'SECRET',
    defaultValue: '',
    hint: '已配置时保持掩码表示不修改；输入新值则替换',
  },
  {
    key: 'ai.model.timeout_seconds',
    labelZh: '模型请求超时（秒）',
    type: 'INTEGER',
    defaultValue: 60,
    min: 10,
    max: 300,
  },
  {
    key: 'ai.model.max_retries',
    labelZh: '模型失败重试次数',
    type: 'INTEGER',
    defaultValue: 2,
    min: 0,
    max: 5,
  },
]

const ALL_FIELDS = [...IMPORT_FIELDS, ...MODEL_FIELDS]
const ALL_KEYS = ALL_FIELDS.map((field) => field.key)

function findItem(key: string): SystemSettingItem | undefined {
  if (!settings.value?.groups) return undefined
  for (const items of Object.values(settings.value.groups)) {
    const found = items.find((item) => item.key === key)
    if (found) return found
  }
  return undefined
}

const missingKeys = computed(() => ALL_KEYS.filter((key) => !findItem(key)))

function valuesEqual(a: unknown, b: unknown) {
  if (typeof a === 'number' || typeof b === 'number') return Number(a) === Number(b)
  return String(a ?? '') === String(b ?? '')
}

function isDirty() {
  return ALL_KEYS.some((key) => !valuesEqual(drafts[key], originals[key]))
}

function syncDraftsFromSettings() {
  for (const key of Object.keys(drafts)) delete drafts[key]
  for (const key of Object.keys(originals)) delete originals[key]
  for (const key of Object.keys(versions)) delete versions[key]
  for (const key of Object.keys(configuredFlags)) delete configuredFlags[key]

  for (const field of ALL_FIELDS) {
    const item = findItem(field.key)
    const value = item ? item.value : field.defaultValue
    drafts[field.key] = value
    originals[field.key] = value
    versions[field.key] = item?.version ?? 0
    configuredFlags[field.key] = Boolean(item?.configured)
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  backendReady.value = false
  try {
    settings.value = await systemSettingsApi.get()
    syncDraftsFromSettings()
    if (missingKeys.value.length) {
      backendReady.value = false
      loadError.value =
        '当前后端尚未提供 AI 配置项。请重新编译并重启后端（包含 SystemSettingRegistry 中的 AI 分组）后再保存。'
      toast.warning('已显示默认表单；保存前请先更新后端')
    } else {
      backendReady.value = true
    }
  } catch (error) {
    settings.value = null
    syncDraftsFromSettings()
    backendReady.value = false
    loadError.value = error instanceof ApiRequestError ? error.message : '加载 AI 配置失败'
    toast.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function buildChanges(): SettingChange[] {
  return ALL_FIELDS.filter((field) => !valuesEqual(drafts[field.key], originals[field.key])).map(
    (field) => ({
      key: field.key,
      value: drafts[field.key],
      expectedVersion: versions[field.key] ?? 0,
    }),
  )
}

async function askSave() {
  if (!backendReady.value) {
    toast.error('后端未就绪，无法保存。请先部署/重启包含 AI 设置的后端。')
    return
  }
  const changes = buildChanges()
  if (!changes.length) {
    toast.warning('没有待保存的修改')
    return
  }
  pendingChanges.value = changes
  confirmOpen.value = true
}

async function doSave() {
  const changes = pendingChanges.value
  if (!changes.length) {
    confirmOpen.value = false
    return
  }
  saving.value = true
  confirmLoading.value = true
  try {
    const validate = await systemSettingsApi.validate(changes)
    if (!validate.valid) {
      toast.error('设置校验未通过，请检查输入后重试')
      return
    }
    settings.value = await systemSettingsApi.update(changes)
    syncDraftsFromSettings()
    confirmOpen.value = false
    toast.success('AI 配置已保存')
  } catch (error) {
    toast.error(error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。')
  } finally {
    saving.value = false
    confirmLoading.value = false
  }
}

function secretHint(field: FieldDef) {
  if (field.type !== 'SECRET') return field.hint ?? ''
  if (configuredFlags[field.key]) {
    return '已配置；保持掩码表示不修改，输入新值则替换'
  }
  return field.hint || '尚未配置 API Key'
}

/** 去掉误填的 /chat/completions，只保留 API 根地址 */
function normalizeBaseUrl(raw: string) {
  let value = raw.trim().replace(/\/+$/, '')
  if (value.toLowerCase().endsWith('/chat/completions')) {
    value = value.slice(0, -'/chat/completions'.length).replace(/\/+$/, '')
  }
  return value
}

async function testConnectivity() {
  testing.value = true
  testResult.value = null
  try {
    const normalizedBase = normalizeBaseUrl(String(drafts['ai.model.base_url'] ?? ''))
    if (normalizedBase && normalizedBase !== String(drafts['ai.model.base_url'] ?? '').trim()) {
      drafts['ai.model.base_url'] = normalizedBase
      toast.warning('已自动去掉地址中的 /chat/completions')
    }
    const apiKeyDraft = String(drafts['ai.model.api_key'] ?? '')
    const result = await adminAiApi.testConnectivity({
      baseUrl: normalizedBase || undefined,
      model: String(drafts['ai.model.name'] ?? '').trim() || undefined,
      apiKey:
        !apiKeyDraft || apiKeyDraft === SECRET_MASK
          ? undefined
          : apiKeyDraft,
    })
    testResult.value = result
    if (result.ok) {
      toast.success(`${result.message}（${result.latencyMs} ms）`)
    } else {
      toast.error(result.message || '联通失败')
    }
  } catch (error) {
    const message = error instanceof ApiRequestError ? error.message : '联通测试失败，请稍后重试'
    testResult.value = {
      ok: false,
      latencyMs: 0,
      model: String(drafts['ai.model.name'] ?? ''),
      replyPreview: '',
      message,
    }
    toast.error(message)
  } finally {
    testing.value = false
  }
}

// 首屏即用默认值填充，避免卡片空白
syncDraftsFromSettings()

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="admin-ai admin-page admin-page--scroll">
    <div class="admin-ai__head">
      <div>
        <p class="admin-ai__hint text-medium-emphasis mb-0">
          智能导入与大模型配置集中管理；API Key 仅支持写入，读取时脱敏显示。
        </p>
      </div>
      <div class="admin-ai__actions">
        <v-btn
          class="admin-toolbar-btn"
          variant="tonal"
          color="primary"
          prepend-icon="mdi-refresh"
          :loading="loading"
          @click="load"
        >
          刷新
        </v-btn>
        <v-btn
          class="admin-toolbar-btn"
          variant="tonal"
          color="primary"
          prepend-icon="mdi-lan-check"
          :loading="testing"
          :disabled="testing || saving"
          @click="testConnectivity"
        >
          测试联通
        </v-btn>
        <v-btn
          class="admin-toolbar-btn"
          color="primary"
          prepend-icon="mdi-content-save-outline"
          :disabled="!backendReady || !isDirty() || saving"
          :loading="saving"
          @click="askSave"
        >
          保存
        </v-btn>
      </div>
    </div>

    <v-alert
      v-if="loadError"
      type="warning"
      variant="tonal"
      density="compact"
      class="mb-0"
    >
      {{ loadError }}
    </v-alert>

    <v-progress-linear v-if="loading && !settings" indeterminate color="primary" class="mb-3" />

    <v-card class="admin-panel admin-ai__card" elevation="0">
      <h2 class="admin-ai__title">智能导入</h2>
      <p class="admin-ai__subtitle text-medium-emphasis">
        控制用户端导入入口、置信度阈值与文件限制
      </p>
      <div class="admin-ai__fields">
        <div v-for="field in IMPORT_FIELDS" :key="field.key" class="admin-ai__field-wrap">
          <v-switch
            v-if="field.type === 'BOOLEAN'"
            v-model="drafts[field.key]"
            class="admin-ai__field"
            :label="field.labelZh"
            color="primary"
            density="compact"
            hide-details
          />
          <v-text-field
            v-else
            v-model.number="drafts[field.key]"
            class="admin-ai__field"
            type="number"
            :label="field.labelZh"
            :min="field.min"
            :max="field.max"
            density="compact"
            hide-details
          />
          <p v-if="field.hint" class="admin-ai__field-hint">{{ field.hint }}</p>
        </div>
      </div>
    </v-card>

    <v-card class="admin-panel admin-ai__card" elevation="0">
      <h2 class="admin-ai__title">模型服务（OpenAI 兼容）</h2>
      <p class="admin-ai__subtitle text-medium-emphasis">
        可对接任意兼容 Chat Completions 的供应商；换模型只需改配置，无需改代码。可用当前表单值测试（未改 Key
        则用已保存密钥）。
      </p>
      <v-alert
        v-if="testResult"
        class="admin-ai__test-result mb-3"
        :type="testResult.ok ? 'success' : 'error'"
        variant="tonal"
        density="compact"
      >
        <div class="admin-ai__test-title">{{ testResult.message }}</div>
        <div class="admin-ai__test-meta text-medium-emphasis">
          <span v-if="testResult.model">模型 {{ testResult.model }}</span>
          <span v-if="testResult.latencyMs">· {{ testResult.latencyMs }} ms</span>
          <span v-if="testResult.ok && testResult.replyPreview">
            · 回复「{{ testResult.replyPreview }}」
          </span>
        </div>
      </v-alert>
      <div class="admin-ai__fields">
        <div v-for="field in MODEL_FIELDS" :key="field.key" class="admin-ai__field-wrap">
          <v-switch
            v-if="field.type === 'BOOLEAN'"
            v-model="drafts[field.key]"
            class="admin-ai__field"
            :label="field.labelZh"
            color="primary"
            density="compact"
            hide-details
          />
          <v-text-field
            v-else-if="field.type === 'SECRET'"
            v-model="drafts[field.key] as string"
            class="admin-ai__field"
            type="password"
            :label="field.labelZh"
            autocomplete="new-password"
            density="compact"
            hide-details
            :placeholder="configuredFlags[field.key] ? '已配置（输入新值以替换）' : '请输入 API Key'"
          />
          <v-text-field
            v-else-if="field.type === 'STRING'"
            v-model="drafts[field.key] as string"
            class="admin-ai__field"
            :label="field.labelZh"
            density="compact"
            hide-details
          />
          <v-text-field
            v-else
            v-model.number="drafts[field.key]"
            class="admin-ai__field"
            type="number"
            :label="field.labelZh"
            :min="field.min"
            :max="field.max"
            density="compact"
            hide-details
          />
          <p v-if="secretHint(field)" class="admin-ai__field-hint">{{ secretHint(field) }}</p>
        </div>
      </div>
    </v-card>

    <OsConfirmDialog
      v-model="confirmOpen"
      title="确认保存 AI 配置？"
      message="将更新智能导入与模型服务相关设置。API Key 变更立即生效。"
      confirm-text="确认保存"
      variant="warning"
      :loading="confirmLoading"
      @confirm="doSave"
    />
  </div>
</template>

<style scoped>
.admin-ai {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 8px;
}

.admin-ai__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.admin-ai__hint {
  font-size: 0.75rem;
  line-height: 1.45;
  max-width: 42rem;
}

.admin-ai__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.admin-ai__card {
  padding: 14px 16px !important;
}

.admin-ai__title {
  margin: 0;
  color: var(--os-text-title);
  font-size: 0.9375rem;
  font-weight: 650;
}

.admin-ai__subtitle {
  margin: 4px 0 12px;
  font-size: 0.75rem;
  line-height: 1.4;
}

.admin-ai__fields {
  display: grid;
  gap: 12px;
}

.admin-ai__field-wrap {
  min-width: 0;
}

.admin-ai__field-hint {
  margin: 4px 0 0;
  color: var(--os-text-muted);
  font-size: 0.75rem;
  line-height: 1.35;
}

.admin-ai__test-title {
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1.35;
}

.admin-ai__test-meta {
  margin-top: 2px;
  font-size: 0.75rem;
  line-height: 1.35;
}

@media (min-width: 900px) {
  .admin-ai__fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-ai__field-wrap:has(.v-switch) {
    grid-column: 1 / -1;
  }
}
</style>
