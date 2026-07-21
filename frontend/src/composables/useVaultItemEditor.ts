import { ref } from 'vue'

const visible = ref(false)
const editingId = ref<string | null>(null)
const templateId = ref<string | null>(null)

export function useVaultItemEditor() {
  function openCreate(options?: { templateId?: string | null }) {
    editingId.value = null
    templateId.value = options?.templateId ?? null
    visible.value = true
  }

  function openEdit(itemId: string) {
    editingId.value = itemId
    templateId.value = null
    visible.value = true
  }

  function close() {
    visible.value = false
    editingId.value = null
    templateId.value = null
  }

  return {
    visible,
    editingId,
    templateId,
    openCreate,
    openEdit,
    close,
  }
}
