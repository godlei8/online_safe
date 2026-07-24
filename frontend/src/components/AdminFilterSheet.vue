<script setup lang="ts">
withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  resultText?: string
}>(), {
  title: '筛选',
  resultText: '查看结果',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'apply'): void
  (e: 'reset'): void
}>()

function close() {
  emit('update:modelValue', false)
}

function apply() {
  emit('apply')
  close()
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    fullscreen
    transition="dialog-bottom-transition"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <v-card class="os-mobile-filters">
      <header class="os-mobile-filters__header">
        <div>
          <p class="vault-eyebrow">快速缩小范围</p>
          <h2>{{ title }}</h2>
        </div>
        <v-btn icon="mdi-close" variant="text" aria-label="关闭筛选" @click="close" />
      </header>
      <div class="os-mobile-filters__body">
        <slot />
      </div>
      <div class="os-mobile-filters__actions">
        <v-btn variant="text" color="primary" @click="emit('reset')">重置</v-btn>
        <v-btn color="primary" @click="apply">{{ resultText }}</v-btn>
      </div>
    </v-card>
  </v-dialog>
</template>
