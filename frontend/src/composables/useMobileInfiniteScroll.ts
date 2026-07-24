import { onMounted, onUnmounted, ref, watch, type Ref } from 'vue'

type MaybeRefBool = Ref<boolean> | { value: boolean }

/**
 * 手机端列表：滚动到底自动加载下一页。
 * 桌面端不绑滚动，继续用分页器。
 */
export function useMobileInfiniteScroll(options: {
  enabled: MaybeRefBool
  loading: MaybeRefBool
  loadingMore: MaybeRefBool
  hasMore: MaybeRefBool
  loadMore: () => void | Promise<void>
  /** 距底部多少像素触发，默认 96 */
  threshold?: number
}) {
  const scrollEl = ref<HTMLElement | null>(null)
  const threshold = options.threshold ?? 96
  let ticking = false

  async function tryLoadMore() {
    if (!options.enabled.value) return
    if (options.loading.value || options.loadingMore.value) return
    if (!options.hasMore.value) return
    await options.loadMore()
  }

  function onScroll() {
    const el = scrollEl.value
    if (!el || !options.enabled.value) return
    if (ticking) return
    ticking = true
    requestAnimationFrame(() => {
      ticking = false
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - threshold) {
        void tryLoadMore()
      }
    })
  }

  function bind() {
    scrollEl.value?.addEventListener('scroll', onScroll, { passive: true })
  }

  function unbind() {
    scrollEl.value?.removeEventListener('scroll', onScroll)
  }

  onMounted(() => {
    bind()
  })

  onUnmounted(unbind)

  watch(scrollEl, (el, prev) => {
    prev?.removeEventListener('scroll', onScroll)
    el?.addEventListener('scroll', onScroll, { passive: true })
  })

  return { scrollEl, onScroll, tryLoadMore }
}
