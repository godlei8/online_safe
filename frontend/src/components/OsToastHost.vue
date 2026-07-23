<script setup lang="ts">
import { computed } from 'vue'
import { useDisplay } from 'vuetify'
import { useOsToast, type OsToastTone } from '@/composables/useOsToast'

const { smAndDown } = useDisplay()
const { visible, text, color, timeoutMs } = useOsToast()

const iconByTone: Record<OsToastTone, string> = {
  error: 'mdi-alert-circle-outline',
  warning: 'mdi-alert-outline',
  success: 'mdi-check-circle-outline',
  primary: 'mdi-information-outline',
  info: 'mdi-information-outline',
}

const icon = computed(() => iconByTone[color.value] ?? iconByTone.info)
</script>

<template>
  <v-snackbar
    v-model="visible"
    class="os-toast"
    :class="`os-toast--${color}`"
    :timeout="timeoutMs"
    :location="smAndDown ? 'bottom' : 'top'"
    :rounded="false"
  >
    <div class="os-toast__inner">
      <span class="os-toast__mark" aria-hidden="true">
        <v-icon :icon="icon" size="14" />
      </span>
      <p class="os-toast__text">{{ text }}</p>
    </div>
  </v-snackbar>
</template>
