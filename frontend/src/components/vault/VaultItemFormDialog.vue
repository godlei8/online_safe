<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useDisplay } from 'vuetify'
import { emptyItemPayload, type VaultItemPayload } from '@/domain/vaultPayload'
import { useVaultItemEditor } from '@/composables/useVaultItemEditor'
import { useVaultStore } from '@/stores/vault'
import VaultItemForm from '@/components/vault/VaultItemForm.vue'

const emit = defineEmits<{
  saved: [id: string]
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
        form.value = structuredClone(item.payload)
      } else if (tmplId) {
        const payload = await vault.buildPayloadFromTemplate(tmplId)
        if (token !== loadToken.value) return
        form.value = payload
      } else {
        form.value = emptyItemPayload()
      }
    } catch (error) {
      if (token !== loadToken.value) return
      errorMessage.value = error instanceof Error ? error.message : '加载失败'
      form.value = emptyItemPayload()
    } finally {
      if (token === loadToken.value) loading.value = false
    }
  },
)

async function save() {
  errorMessage.value = ''
  if (!form.value.name.trim() || !form.value.platform.trim()) {
    errorMessage.value = '请填写记录名称和平台'
    return
  }
  for (const field of form.value.fields) {
    if (field.required && !field.value.trim()) {
      errorMessage.value = `请填写必填字段：${field.name}`
      return
    }
  }
  saving.value = true
  try {
    const id = await vault.saveItem(editor.editingId.value, form.value)
    editor.close()
    emit('saved', id)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
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
    :max-width="smAndDown ? undefined : 800"
    scrollable
    persistent
  >
    <v-card class="vault-item-form-dialog">
      <v-card-title class="vault-item-form-dialog__title">
        <div>
          <p class="vault-item-form-dialog__eyebrow">{{ title }}</p>
          <h2>{{ subtitle }}</h2>
        </div>
        <v-btn
          icon="mdi-close"
          variant="text"
          aria-label="关闭"
          @click="cancel"
        />
      </v-card-title>

      <v-card-text class="vault-item-form-dialog__body">
        <VaultItemForm
          v-model="form"
          :loading="loading"
          :error-message="errorMessage"
        />
      </v-card-text>

      <v-card-actions class="vault-item-form-dialog__actions">
        <v-spacer />
        <v-btn variant="text" :disabled="saving" @click="cancel">取消</v-btn>
        <v-btn color="primary" :loading="saving" :disabled="loading" @click="save">
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
  max-height: min(90vh, 920px);
}

.vault-item-form-dialog__title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px 12px !important;
  border-bottom: 1px solid #eef2f7;
}

.vault-item-form-dialog__eyebrow {
  margin: 0;
  color: #64748b;
  font-size: 0.75rem;
  font-weight: 600;
}

.vault-item-form-dialog__title h2 {
  margin: 2px 0 0;
  color: #0f172a;
  font-size: 1.15rem;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.vault-item-form-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 16px 20px !important;
}

.vault-item-form-dialog__actions {
  flex: 0 0 auto;
  padding: 12px 20px !important;
  border-top: 1px solid #eef2f7;
  background: #fff;
}
</style>
