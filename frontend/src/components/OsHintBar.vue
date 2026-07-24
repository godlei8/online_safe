<script setup lang="ts">
import { ref, watch } from 'vue'
import { useDisplay } from 'vuetify'

withDefaults(defineProps<{
  tone?: 'info' | 'success' | 'warning' | 'error'
  icon?: string
}>(), {
  tone: 'info',
  icon: 'mdi-information',
})

const { xs } = useDisplay()
/** 手机端默认收起，省纵向空间 */
const expanded = ref(false)

watch(xs, (isXs) => {
  if (!isXs) expanded.value = true
  else expanded.value = false
}, { immediate: true })
</script>

<template>
  <div
    class="os-hint"
    :class="[
      `os-hint--${tone}`,
      { 'os-hint--collapsed': xs && !expanded, 'os-hint--expanded': xs && expanded },
    ]"
    role="status"
  >
    <template v-if="xs">
      <button
        type="button"
        class="os-hint__toggle"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
      >
        <v-icon class="os-hint__icon" :icon="icon" size="14" aria-hidden="true" />
        <span class="os-hint__toggle-label">说明</span>
        <v-icon :icon="expanded ? 'mdi-chevron-up' : 'mdi-chevron-down'" size="16" aria-hidden="true" />
      </button>
      <div v-show="expanded" class="os-hint__body">
        <slot />
      </div>
    </template>
    <template v-else>
      <v-icon class="os-hint__icon" :icon="icon" size="14" aria-hidden="true" />
      <div class="os-hint__body"><slot /></div>
    </template>
  </div>
</template>
