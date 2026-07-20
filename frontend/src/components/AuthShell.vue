<script setup lang="ts">
withDefaults(defineProps<{ register?: boolean }>(), { register: false })
</script>

<template>
  <div class="auth-page" :class="{ 'auth-page--register': register }">
    <header class="auth-header">
      <router-link to="/login" class="auth-brand text-decoration-none" aria-label="Online Safe 登录页">
        <span class="auth-brand__mark"><v-icon icon="mdi-shield-lock-outline" size="20" /></span>
        <span>Online Safe</span>
      </router-link>
      <div class="auth-header__actions">
        <span class="auth-header__status d-none d-sm-inline-flex">
          <v-icon icon="mdi-shield-check-outline" size="16" />
          安全访问
        </span>
        <router-link v-if="register" to="/login" class="auth-header__link">已有账号，去登录</router-link>
        <router-link v-else to="/register" class="auth-header__link">创建账号</router-link>
      </div>
    </header>

    <main class="auth-main">
      <aside class="auth-aside" aria-label="Online Safe 安全说明">
        <div class="auth-aside__inner">
          <span class="auth-eyebrow"><v-icon icon="mdi-shield-check-outline" size="15" /> 账号信息，清晰掌控</span>
          <h1>把重要账号<br />放在安心的位置</h1>
          <p>为不同渠道获得的账号资料，建立统一、清晰且可持续扩展的个人管理入口。</p>

          <div class="auth-security-list">
            <div class="auth-security-item">
              <v-avatar size="40" rounded="lg"><v-icon icon="mdi-account-lock-outline" size="20" /></v-avatar>
              <div><strong>个人账号独立保存</strong><span>每位用户只管理自己的账号资料。</span></div>
            </div>
            <div class="auth-security-item">
              <v-avatar size="40" rounded="lg"><v-icon icon="mdi-layers-triple-outline" size="20" /></v-avatar>
              <div><strong>记录字段可扩展</strong><span>适配不同来源和不同类型的账号信息。</span></div>
            </div>
            <div class="auth-security-item">
              <v-avatar size="40" rounded="lg"><v-icon icon="mdi-shield-key-outline" size="20" /></v-avatar>
              <div><strong>访问边界更清晰</strong><span>个人用户与管理员使用独立入口。</span></div>
            </div>
          </div>

          <div class="auth-aside__visual" aria-hidden="true">
            <span /><span /><span /><span /><span /><span />
          </div>
        </div>
      </aside>

      <section class="auth-content">
        <div class="auth-mobile-context d-md-none">
          <v-icon icon="mdi-shield-check-outline" size="18" />
          <span>为你的账号信息建立清晰的管理入口</span>
        </div>
        <v-card class="auth-card" :class="{ 'auth-card--register': register }" elevation="0">
          <slot />
        </v-card>
      </section>
    </main>

    <footer class="auth-footer">
      <span>© 2026 Online Safe</span>
      <span class="d-none d-sm-inline">安全保存，清晰掌控</span>
      <span class="d-none d-md-inline">个人资料管理工具</span>
    </footer>
  </div>
</template>
