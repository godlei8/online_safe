<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  channelExternalHref,
  effectiveStatus,
  fieldTypeLabel,
  statusLabel,
  toExternalHref,
  type VaultItemPayload,
} from '@/domain/vaultPayload'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useVaultStore } from '@/stores/vault'

const route = useRoute()
const router = useRouter()
const vault = useVaultStore()
const editor = useVaultItemEditor()

const loading = ref(true)
const errorMessage = ref('')
const snackbar = ref(false)
const snackbarText = ref('')
const payload = ref<VaultItemPayload | null>(null)
const updatedAt = ref('')
const hiddenFields = ref<Record<string, boolean>>({})

const sortedFields = computed(() =>
  [...(payload.value?.fields ?? [])].sort((a, b) => a.order - b.order),
)

function initHiddenFields(fields: VaultItemPayload['fields']) {
  const next: Record<string, boolean> = {}
  for (const field of fields) {
    // 敏感字段默认暗文隐藏
    if (field.sensitive || field.type === 'PASSWORD' || field.systemKey === 'password') {
      next[field.id] = true
    }
  }
  hiddenFields.value = next
}

async function loadItem() {
  loading.value = true
  errorMessage.value = ''
  try {
    const item = await vault.getItem(String(route.params.id))
    payload.value = item.payload
    updatedAt.value = item.envelope.updatedAt ?? ''
    initHiddenFields(item.payload.fields)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
    payload.value = null
    hiddenFields.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(loadItem)

watch(
  () => [route.params.id, route.query.refreshed] as const,
  () => {
    loadItem()
  },
)

async function copyValue(value: string, label: string) {
  await navigator.clipboard.writeText(value)
  snackbarText.value = `已复制${label}`
  snackbar.value = true
}

function toggleHide(fieldId: string) {
  const currentlyMasked = hiddenFields.value[fieldId] !== false
  hiddenFields.value[fieldId] = !currentlyMasked
}

function isMasked(field: { id: string; sensitive: boolean; type: string; systemKey?: string }) {
  if (!(field.sensitive || field.type === 'PASSWORD' || field.systemKey === 'password')) return false
  return hiddenFields.value[field.id] !== false
}

function displayValue(field: { id: string; sensitive: boolean; type: string; systemKey?: string }, value: string) {
  if (isMasked(field)) return '••••••••'
  return value || '（空）'
}

function showTypeLabel(field: { name: string; type: string }) {
  const typeLabel = fieldTypeLabel(field.type)
  return typeLabel && typeLabel !== field.name
}

function openEdit() {
  editor.openEdit(String(route.params.id))
}

const displayStatus = computed(() => (
  payload.value ? statusLabel[effectiveStatus(payload.value)] : ''
))

const channelHref = computed(() => (
  payload.value ? channelExternalHref(payload.value) : null
))

const statusTone = computed(() => {
  const status = displayStatus.value
  if (status === '正常') return 'ok'
  if (status === '异常') return 'abnormal'
  if (status === '过期') return 'expired'
  return 'archived'
})

</script>

<template>
  <section class="vault-content vault-detail-panel">
    <div class="vault-detail-panel__toolbar">
      <v-btn variant="text" prepend-icon="mdi-arrow-left" @click="router.push('/vault')">
        返回列表
      </v-btn>
      <v-btn variant="tonal" color="primary" @click="openEdit">编辑</v-btn>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-alert v-else-if="errorMessage" type="error" variant="tonal">{{ errorMessage }}</v-alert>
    <template v-else-if="payload">
      <header class="vault-detail-panel__header">
        <p class="vault-eyebrow">记录详情</p>
        <h1>{{ payload.name }}</h1>
        <p class="vault-detail-panel__meta">
          <span>{{ payload.platform }}</span>
          <template v-if="payload.channel || channelHref">
            <span class="vault-detail-panel__dot">·</span>
            <span class="vault-detail-panel__channel-label">来源</span>
            <a
              v-if="channelHref"
              class="vault-detail-panel__channel"
              :href="channelHref"
              target="_blank"
              rel="noopener noreferrer"
            >{{ payload.channel || payload.channelUrl }}</a>
            <span v-else class="vault-detail-panel__channel">{{ payload.channel }}</span>
          </template>
          <span class="vault-detail-panel__dot">·</span>
          <span class="vault-detail-panel__status" :data-tone="statusTone">{{ displayStatus }}</span>
          <template v-if="payload.expiresAt">
            <span class="vault-detail-panel__dot">·</span>
            <span>有效期 {{ payload.expiresAt.slice(0, 10) }}</span>
          </template>
          <template v-else>
            <span class="vault-detail-panel__dot">·</span>
            <span>永久有效</span>
          </template>
        </p>
        <p v-if="updatedAt" class="vault-meta">
          更新于 {{ new Date(updatedAt).toLocaleString('zh-CN') }}
        </p>
      </header>

      <v-alert type="info" variant="tonal" class="mb-4">
        敏感字段默认以暗文显示，可点击眼睛图标临时查看；网址可点击跳转，并支持一键复制。
      </v-alert>

      <div class="vault-field-list">
        <div v-for="field in sortedFields" :key="field.id" class="vault-field-row">
          <div>
            <strong>{{ field.name }}</strong>
            <span v-if="showTypeLabel(field)" class="vault-field-row__type">{{ fieldTypeLabel(field.type) }}</span>
            <div class="vault-field-row__value">
              <a
                v-if="field.type === 'URL' && toExternalHref(field.value) && !isMasked(field)"
                :href="toExternalHref(field.value)!"
                class="vault-field-row__link"
                target="_blank"
                rel="noopener noreferrer"
              >
                {{ field.value }}
              </a>
              <template v-else>
                {{ displayValue(field, field.value) }}
              </template>
            </div>
          </div>
          <div class="vault-field-row__actions">
            <v-btn
              v-if="field.sensitive || field.type === 'PASSWORD' || field.systemKey === 'password'"
              size="small"
              variant="text"
              :icon="isMasked(field) ? 'mdi-eye-outline' : 'mdi-eye-off-outline'"
              :aria-label="isMasked(field) ? '显示' : '隐藏'"
              @click="toggleHide(field.id)"
            />
            <v-btn
              v-if="field.copyable"
              size="small"
              variant="tonal"
              prepend-icon="mdi-content-copy"
              @click="copyValue(field.value, field.name)"
            >
              复制
            </v-btn>
          </div>
        </div>
      </div>

      <section v-if="payload.notes" class="mt-6">
        <h2>备注</h2>
        <p class="vault-notes">{{ payload.notes }}</p>
      </section>
    </template>

    <v-snackbar v-model="snackbar" class="vault-feedback-snackbar" location="bottom" :timeout="2000">{{ snackbarText }}</v-snackbar>
  </section>
</template>

<style scoped>
.vault-detail-panel__toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.vault-detail-panel__header h1 {
  font-size: 1.75rem;
  margin: 4px 0;
}

.vault-eyebrow {
  color: var(--os-text-muted);
  font-size: 0.85rem;
}

.vault-meta {
  color: var(--os-text-muted);
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
}

.vault-detail-panel__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.2em;
  color: var(--os-text-body);
  line-height: 1.5;
  word-break: break-all;
}

.vault-detail-panel__dot {
  color: var(--os-text-muted);
}

.vault-detail-panel__channel-label {
  color: var(--os-text-muted);
  margin-right: 0.5em;
}

.vault-detail-panel__channel {
  color: #e67e22;
  font-weight: 600;
  text-decoration: none;
}

a.vault-detail-panel__channel:hover {
  text-decoration: underline;
}

.vault-detail-panel__status {
  font-weight: 600;
}

.vault-detail-panel__status[data-tone='ok'] {
  color: var(--os-success);
}

.vault-detail-panel__status[data-tone='abnormal'] {
  color: var(--os-abnormal);
}

.vault-detail-panel__status[data-tone='expired'] {
  color: var(--os-warning);
}

.vault-detail-panel__status[data-tone='archived'] {
  color: var(--os-text-muted);
}

.vault-field-list {
  display: grid;
  gap: 12px;
}

/* 字段卡：与记录卡同语言（1px 边框 + Level 1 环境阴影） */
.vault-field-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: var(--os-radius-card);
  background: var(--os-surface);
  border: 1px solid var(--os-border);
  box-shadow: var(--os-shadow-1);
}

.vault-field-row__type {
  margin-left: 8px;
  color: var(--os-text-muted);
  font-size: 0.8rem;
}

.vault-field-row__value {
  margin-top: 6px;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.vault-field-row__link {
  color: var(--os-primary);
  font-weight: 600;
  text-decoration: none;
  font-family: inherit;
}

.vault-field-row__link:hover {
  text-decoration: underline;
}

.vault-field-row__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.vault-notes {
  white-space: pre-wrap;
  color: var(--os-text-body);
}

@media (max-width: 599px) {
  .vault-detail-panel__toolbar,
  .vault-field-row {
    align-items: stretch;
  }

  .vault-field-row {
    flex-direction: column;
  }

  .vault-field-row__actions {
    justify-content: flex-end;
  }

  .vault-field-row__actions .v-btn {
    min-height: 44px;
  }
}
</style>
