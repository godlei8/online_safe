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
const fromTemplate = computed(() => Boolean(editor.templateId.value) && !isEdit.value)
const title = computed(() => {
  if (isEdit.value) return '编辑记录'
  if (fromTemplate.value) {
    return editor.templateSource.value === 'system' ? '系统模板创建' : '模板创建'
  }
  return '手动录入账密'
})
const subtitle = computed(() => {
  if (isEdit.value) return '修改账密记录'
  if (fromTemplate.value) return '按模板结构填写账密'
  return '新增账密记录'
})

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
  () => [
    editor.visible.value,
    editor.editingId.value,
    editor.templateId.value,
    editor.templateSource.value,
  ] as const,
  async ([visible, itemId, tmplId, tmplSource]) => {
    if (!visible) return
    const token = ++loadToken.value
    errorMessage.value = ''
    loading.value = true
    try {
      if (itemId) {
        const item = await vault.getItem(itemId)
        if (token !== loadToken.value) return
        form.value = cloneVaultItemPayload(item.payload)
      } else if (tmplId && tmplSource === 'system') {
        const payload = await vault.buildPayloadFromSystemTemplate(tmplId)
        if (token !== loadToken.value) return
        form.value = cloneVaultItemPayload(payload)
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
    <v-card class="os-form-dialog">
      <v-card-title class="os-form-dialog__title">
        <div class="os-form-dialog__heading">
          <span class="os-form-dialog__mark" aria-hidden="true">
            <v-icon :icon="isEdit ? 'mdi-pencil-outline' : 'mdi-plus-circle-outline'" size="18" />
          </span>
          <div>
            <p class="os-form-dialog__eyebrow">{{ title }}</p>
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

      <v-card-text class="os-form-dialog__body">
        <VaultItemForm
          v-model="form"
          :loading="loading"
          :error-message="errorMessage"
          :mode="isEdit ? 'edit' : 'create'"
        />
      </v-card-text>

      <v-card-actions class="os-form-dialog__actions">
        <v-spacer />
        <v-btn variant="text" size="small" :disabled="saving" @click="cancel">取消</v-btn>
        <v-btn
          class="os-form-dialog__save"
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
