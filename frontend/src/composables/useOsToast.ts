import { ref } from 'vue'

export type OsToastTone = 'error' | 'success' | 'warning' | 'primary' | 'info'

const visible = ref(false)
const text = ref('')
const color = ref<OsToastTone>('error')
const timeoutMs = ref(2600)

/**
 * 全局轻提示：表单校验失败、保存失败等用独立弹出，不占用弹窗内横幅。
 */
export function useOsToast() {
  function show(message: string, tone: OsToastTone = 'error', ms = 2600) {
    const next = message.trim()
    if (!next) return
    text.value = next
    color.value = tone
    timeoutMs.value = ms
    // 连续触发时先关再开，确保 snackbar 重新计时展示
    if (visible.value) {
      visible.value = false
      requestAnimationFrame(() => {
        visible.value = true
      })
      return
    }
    visible.value = true
  }

  return {
    visible,
    text,
    color,
    timeoutMs,
    show,
    error: (message: string, ms?: number) => show(message, 'error', ms ?? 2800),
    success: (message: string, ms?: number) => show(message, 'success', ms ?? 2200),
    warning: (message: string, ms?: number) => show(message, 'warning', ms ?? 2600),
  }
}
