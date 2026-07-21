<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { statusLabel, type VaultItemPayload } from '@/domain/vaultPayload'
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

async function loadItem() {
  loading.value = true
  errorMessage.value = ''
  try {
    const item = await vault.getItem(String(route.params.id))
    payload.value = item.payload
    updatedAt.value = item.envelope.updatedAt ?? ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
    payload.value = null
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
  hiddenFields.value[fieldId] = !hiddenFields.value[fieldId]
}

function displayValue(fieldId: string, value: string, sensitive: boolean) {
  if (sensitive && hiddenFields.value[fieldId]) return '••••••••'
  return value || '（空）'
}

function openEdit() {
  editor.openEdit(String(route.params.id))
}
</script>

<template>
  <section class="vault-content vault-detail-panel">
    <div class="vault-detail-panel__toolbar">
      <v-btn variant="text" prepend-icon="mdi-arrow-left" @click="router.push('/vault')">
        返回列表
      </v-btn>
      <v-btn variant="tonal" @click="openEdit">编辑</v-btn>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-alert v-else-if="errorMessage" type="error" variant="tonal">{{ errorMessage }}</v-alert>
    <template v-else-if="payload">
      <header class="vault-detail-panel__header">
        <p class="vault-eyebrow">记录详情</p>
        <h1>{{ payload.name }}</h1>
        <p>{{ payload.platform }} · {{ payload.channel || '未填写渠道' }} · {{ statusLabel[payload.status] }}</p>
        <p v-if="updatedAt" class="vault-meta">
          更新于 {{ new Date(updatedAt).toLocaleString('zh-CN') }}
        </p>
      </header>

      <v-alert type="info" variant="tonal" class="mb-4">
        敏感字段默认明文显示；可按字段临时隐藏，并支持一键复制。
      </v-alert>

      <div class="vault-field-list">
        <div v-for="field in sortedFields" :key="field.id" class="vault-field-row">
          <div>
            <strong>{{ field.name }}</strong>
            <span class="vault-field-row__type">{{ field.type }}</span>
            <div class="vault-field-row__value">
              {{ displayValue(field.id, field.value, field.sensitive) }}
            </div>
          </div>
          <div class="vault-field-row__actions">
            <v-btn
              v-if="field.sensitive"
              size="small"
              variant="text"
              :icon="hiddenFields[field.id] ? 'mdi-eye-outline' : 'mdi-eye-off-outline'"
              :aria-label="hiddenFields[field.id] ? '显示' : '隐藏'"
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

    <v-snackbar v-model="snackbar" :timeout="2000">{{ snackbarText }}</v-snackbar>
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
  color: #64748b;
  font-size: 0.85rem;
}

.vault-meta {
  color: #64748b;
  font-size: 0.9rem;
}

.vault-field-list {
  display: grid;
  gap: 12px;
}

.vault-field-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
}

.vault-field-row__type {
  margin-left: 8px;
  color: #94a3b8;
  font-size: 0.8rem;
}

.vault-field-row__value {
  margin-top: 6px;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.vault-field-row__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.vault-notes {
  white-space: pre-wrap;
  color: #334155;
}
</style>
