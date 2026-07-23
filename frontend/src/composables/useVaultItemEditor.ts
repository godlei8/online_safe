import { ref } from 'vue'

export type TemplateSource = 'private' | 'system'

const visible = ref(false)
const editingId = ref<string | null>(null)
const templateId = ref<string | null>(null)
const templateSource = ref<TemplateSource | null>(null)

export function useVaultItemEditor() {
  function openCreate(options?: { templateId?: string | null; templateSource?: TemplateSource | null }) {
    editingId.value = null
    templateId.value = options?.templateId ?? null
    templateSource.value = options?.templateSource ?? (options?.templateId ? 'private' : null)
    visible.value = true
  }

  function openEdit(itemId: string) {
    editingId.value = itemId
    templateId.value = null
    templateSource.value = null
    visible.value = true
  }

  function close() {
    visible.value = false
    editingId.value = null
    templateId.value = null
    templateSource.value = null
  }

  return {
    visible,
    editingId,
    templateId,
    templateSource,
    openCreate,
    openEdit,
    close,
  }
}
