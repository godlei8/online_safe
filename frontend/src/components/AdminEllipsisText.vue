<script setup lang="ts">
import { useAttrs } from 'vue'

withDefaults(defineProps<{
  text?: string | null
  /** 空值占位 */
  empty?: string
  /** 最大宽度，如 160px / 12rem */
  maxWidth?: string
}>(), {
  text: '',
  empty: '—',
  maxWidth: '12rem',
})

defineOptions({ inheritAttrs: false })

const attrs = useAttrs()
</script>

<template>
  <v-tooltip
    :text="text?.trim() || empty"
    location="top"
    open-delay="280"
    :disabled="!text?.trim()"
  >
    <template #activator="{ props: tipProps }">
      <span
        v-bind="{ ...tipProps, ...attrs }"
        class="admin-ellipsis"
        :style="{ maxWidth }"
      >{{ text?.trim() || empty }}</span>
    </template>
  </v-tooltip>
</template>
