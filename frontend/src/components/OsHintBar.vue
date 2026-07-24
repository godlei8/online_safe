<script setup lang="ts">
withDefaults(defineProps<{
  tone?: 'info' | 'success' | 'warning' | 'error'
  icon?: string
}>(), {
  tone: 'info',
  icon: 'mdi-information',
})
</script>

<template>
  <div class="os-hint" :class="`os-hint--${tone}`" role="status">
    <v-icon class="os-hint__icon" :icon="icon" size="14" aria-hidden="true" />
    <div class="os-hint__body"><slot /></div>
  </div>
</template>
