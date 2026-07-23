<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import {
  cloneVaultItemPayload,
  effectiveStatus,
  emptyItemPayload,
  ensureCredentialFields,
  type VaultItemPayload,
} from '@/domain/vaultPayload'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useVaultStore } from '@/stores/vault'
import VaultItemForm from '@/components/vault/VaultItemForm.vue'

const emit = defineEmits<{
  saved: [id: string, created: boolean]
}>()

const { smAndDown } = useDisplay()
const vault = useVaultStore()
const editor = useVaultItemEditor()

const form = ref<VaultItemPayload>(emptyItemPayload())
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const loadToken = ref(0)

const isEdit = computed(() => Boolean(editor.editingId.value))
const title = computed(() => (isEdit.value ? '编辑记录' : '手动录入账密'))
const subtitle = computed(() => (isEdit.value ? '修改账密记录' : '新增账密记录'))

const dialogOpen = computed({
  get: () => editor.visible.value,
  set: (value: boolean) => {
    if (!value) editor.close()
  },
})

function friendlyError(error: unknown, fallback: string): string {
  if (!(error instanceof Error)) return fallback
  // 业务侧已写中文的提示直接展示；浏览器英文异常不透出
  if (/[\u4e00-\u9fff]/.test(error.message)) return error.message
  return fallback
}

watch(
  () => [editor.visible.value, editor.editingId.value, editor.templateId.value] as const,
  async ([visible, itemId, tmplId]) => {
    if (!visible) return
    const token = ++loadToken.value
    errorMessage.value = ''
    loading.value = true
    try {
      if (itemId) {
        const item = await vault.getItem(itemId)
        if (token !== loadToken.value) return
        form.value = cloneVaultItemPayload(item.payload)
      } else if (tmplId) {
        const payload = await vault.buildPayloadFromTemplate(tmplId)
        if (token !== loadToken.value) return
        form.value = cloneVaultItemPayload(payload)
      } else {
        form.value = emptyItemPayload()
      }
    } catch (error) {
      if (token !== loadToken.value) return
      errorMessage.value = friendlyError(error, '加载记录失败，请重试')
      form.value = emptyItemPayload()
    } finally {
      if (token === loadToken.value) loading.value = false
    }
  },
)

async function save() {
  errorMessage.value = ''
  const draft = ensureCredentialFields(form.value)
  if (!draft.name.trim() || !draft.platform.trim()) {
    errorMessage.value = '请填写记录名称和平台'
    return
  }
  const account = draft.fields.find((field) => field.systemKey === 'account')
  const password = draft.fields.find((field) => field.systemKey === 'password')
  if (!account?.value.trim() || !password?.value.trim()) {
    errorMessage.value = '请填写账号和密码'
    return
  }
  for (const field of draft.fields) {
    if (field.required && !field.value.trim()) {
      errorMessage.value = `请填写必填字段：${field.name}`
      return
    }
  }

  // 新建固定正常；编辑保留异常，其余按有效期计算
  if (!isEdit.value) {
    draft.status = 'NORMAL'
  } else if (draft.status !== 'ABNORMAL') {
    draft.status = effectiveStatus({ ...draft, status: 'NORMAL' })
  }
  draft.tags = []
  form.value = draft

  saving.value = true
  const created = !isEdit.value
  try {
    const id = await vault.saveItem(editor.editingId.value, draft)
    editor.close()
    emit('saved', id, created)
  } catch (error) {
    errorMessage.value = friendlyError(error, '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

function cancel() {
  editor.close()
}
</script>

<template>
  <v-dialog
    v-model="dialogOpen"
    :fullscreen="smAndDown"
    :max-width="smAndDown ? undefined : 720"
    scrollable
    persistent
  >
    <v-card class="vault-item-form-dialog">
      <v-card-title class="vault-item-form-dialog__title">
        <div class="vault-item-form-dialog__heading">
          <span class="vault-item-form-dialog__mark" aria-hidden="true">
            <v-icon :icon="isEdit ? 'mdi-pencil-outline' : 'mdi-plus-circle-outline'" size="18" />
          </span>
          <div>
            <p class="vault-item-form-dialog__eyebrow">{{ title }}</p>
            <h2>{{ subtitle }}</h2>
          </div>
        </div>
        <v-btn
          icon="mdi-close"
          variant="text"
          size="small"
          aria-label="关闭"
          @click="cancel"
        />
      </v-card-title>

      <v-card-text class="vault-item-form-dialog__body">
        <VaultItemForm
          v-model="form"
          :loading="loading"
          :error-message="errorMessage"
          :mode="isEdit ? 'edit' : 'create'"
        />
      </v-card-text>

      <v-card-actions class="vault-item-form-dialog__actions">
        <v-spacer />
        <v-btn variant="text" size="small" :disabled="saving" @click="cancel">取消</v-btn>
        <v-btn
          class="vault-item-form-dialog__save"
          color="primary"
          size="small"
          :loading="saving"
          :disabled="loading"
          @click="save"
        >
          保存
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.vault-item-form-dialog {
  display: flex;
  flex-direction: column;
  max-height: min(90vh, 860px);
  overflow: hidden;
}

.vault-item-form-dialog__title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px 10px !important;
  border-bottom: 1px solid var(--os-border);
  background:
    linear-gradient(120deg, rgb(21 94 239 / 8%), transparent 55%),
    var(--os-surface);
}

.vault-item-form-dialog__heading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.vault-item-form-dialog__mark {
  display: inline-grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 9px;
  color: #fff;
  background: linear-gradient(145deg, #3b82f6, #155eef);
  box-shadow: 0 6px 14px -6px rgb(21 94 239 / 55%);
}

.vault-item-form-dialog__eyebrow {
  margin: 0;
  color: var(--os-primary);
  font-size: 0.6875rem;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.vault-item-form-dialog__title h2 {
  margin: 1px 0 0;
  color: var(--os-text-title);
  font-size: 1.05rem;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.vault-item-form-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 12px 10px 12px 14px !important;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: linear-gradient(180deg, #f8faff 0%, var(--os-bg) 48%);
  scrollbar-gutter: stable;
}

.vault-item-form-dialog__actions {
  flex: 0 0 auto;
  padding: 10px 14px !important;
  border-top: 1px solid var(--os-border);
  background: var(--os-surface);
}

.vault-item-form-dialog__save {
  min-width: 84px;
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.vault-item-form-dialog__save:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 16px -8px rgb(21 94 239 / 55%);
}

@media (prefers-reduced-motion: reduce) {
  .vault-item-form-dialog__save {
    transition: none;
  }

  .vault-item-form-dialog__save:hover {
    transform: none;
  }
}
</style>
