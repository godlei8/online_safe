<script setup lang="ts">
import { computed } from 'vue'

export type OsConfirmVariant = 'danger' | 'warning' | 'primary'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  variant?: OsConfirmVariant
  loading?: boolean
  icon?: string
}>(), {
  confirmText: '确认',
  cancelText: '取消',
  variant: 'danger',
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const markIcon = computed(() => {
  if (props.icon) return props.icon
  if (props.variant === 'warning') return 'mdi-alert-outline'
  if (props.variant === 'primary') return 'mdi-help-circle-outline'
  return 'mdi-delete-alert-outline'
})

const confirmColor = computed(() => {
  if (props.variant === 'warning') return 'warning'
  if (props.variant === 'primary') return 'primary'
  return 'error'
})

function onCancel() {
  if (props.loading) return
  open.value = false
  emit('cancel')
}

function onConfirm() {
  if (props.loading) return
  emit('confirm')
}
</script>

<template>
  <v-dialog
    v-model="open"
    class="os-confirm-dialog-host"
    max-width="400"
    :persistent="loading"
    transition="os-confirm-transition"
  >
    <v-card
      class="os-confirm-dialog"
      :class="`os-confirm-dialog--${variant}`"
      role="alertdialog"
      :aria-label="title"
    >
      <div class="os-confirm-dialog__glow" aria-hidden="true" />
      <div class="os-confirm-dialog__head">
        <span class="os-confirm-dialog__mark" aria-hidden="true">
          <v-icon :icon="markIcon" size="20" />
        </span>
        <div class="os-confirm-dialog__copy">
          <h2 class="os-confirm-dialog__title">{{ title }}</h2>
          <p class="os-confirm-dialog__message">{{ message }}</p>
        </div>
      </div>
      <div class="os-confirm-dialog__actions">
        <v-btn
          class="os-confirm-dialog__cancel"
          variant="text"
          size="small"
          :disabled="loading"
          @click="onCancel"
        >
          {{ cancelText }}
        </v-btn>
        <v-btn
          class="os-confirm-dialog__confirm"
          :color="confirmColor"
          variant="flat"
          size="small"
          :loading="loading"
          @click="onConfirm"
        >
          {{ confirmText }}
        </v-btn>
      </div>
    </v-card>
  </v-dialog>
</template>
