<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const active = computed(() => (route.path.includes('/security/data') ? 'data' : 'devices'))
</script>

<template>
  <nav class="vault-security-nav" role="tablist" aria-label="安全中心分区">
    <RouterLink
      class="vault-security-nav__link"
      :class="{ 'is-active': active === 'devices' }"
      role="tab"
      :aria-selected="active === 'devices'"
      to="/vault/security"
    >
      登录设备
    </RouterLink>
    <RouterLink
      class="vault-security-nav__link"
      :class="{ 'is-active': active === 'data' }"
      role="tab"
      :aria-selected="active === 'data'"
      to="/vault/security/data"
    >
      数据安全与恢复
    </RouterLink>
  </nav>
</template>

<style scoped>
.vault-security-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 4px 0 12px;
  border-bottom: 1px solid var(--os-border);
}

.vault-security-nav__link {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 4px 2px 8px;
  margin-bottom: -1px;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--os-text-muted);
  background: transparent;
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.25;
  text-decoration: none;
  transition:
    color var(--os-duration-fast, 160ms) ease,
    border-color var(--os-duration-fast, 160ms) ease;
}

.vault-security-nav__link:hover {
  color: var(--os-text-title);
}

.vault-security-nav__link.is-active {
  color: var(--os-primary);
  border-bottom-color: var(--os-primary);
}

.vault-security-nav__link:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--os-primary) 55%, transparent);
  outline-offset: 2px;
  border-radius: 4px;
}
</style>
