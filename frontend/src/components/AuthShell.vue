<script setup lang="ts">
import loginVaultIllustration from '@/assets/login-vault-illustration.png'

withDefaults(defineProps<{
  register?: boolean
  /** 管理端登录：隐藏个人注册入口，品牌不链回个人登录 */
  admin?: boolean
}>(), {
  register: false,
  admin: false,
})
</script>

<template>
  <div class="auth-page" :class="{ 'auth-page--register': register, 'auth-page--admin': admin }">
    <header class="auth-header">
      <router-link
        v-if="!admin"
        to="/login"
        class="auth-brand text-decoration-none"
        aria-label="Online Safe 登录页"
      >
        <span class="auth-brand__mark"><v-icon icon="mdi-shield-lock-outline" size="20" /></span>
        <span>Online Safe</span>
      </router-link>
      <div v-else class="auth-brand" aria-label="Online Safe 管理后台">
        <span class="auth-brand__mark"><v-icon icon="mdi-shield-lock-outline" size="20" /></span>
        <span>Online Safe Admin</span>
      </div>
      <div class="auth-header__actions">
        <span class="auth-header__status d-none d-sm-inline-flex">
          <v-icon icon="mdi-shield-check-outline" size="16" />
          {{ admin ? '管理端访问' : '安全访问' }}
        </span>
        <template v-if="!admin">
          <router-link v-if="register" to="/login" class="auth-header__link">已有账号，去登录</router-link>
          <router-link v-else to="/register" class="auth-header__link">创建账号</router-link>
        </template>
      </div>
    </header>

    <main class="auth-main">
      <aside class="auth-aside" :aria-label="admin ? 'Online Safe 管理端说明' : 'Online Safe 安全说明'">
        <div class="auth-aside__inner">
          <template v-if="admin">
            <span class="auth-eyebrow"><v-icon icon="mdi-shield-account-outline" size="15" /> 管理后台</span>
            <h1>系统运营<br />独立入口</h1>
            <p>管理员专用通道，用于用户、邀请码、公告与系统策略管理；与个人保险箱完全隔离。</p>

            <div class="auth-security-list auth-security-list--compact">
              <div class="auth-security-item">
                <v-avatar size="36" rounded="lg"><v-icon icon="mdi-account-group-outline" size="18" /></v-avatar>
                <div><strong>只管系统，不看明文</strong><span>管理员仅可见账户元数据，无法打开用户保险箱。</span></div>
              </div>
              <div class="auth-security-item">
                <v-avatar size="36" rounded="lg"><v-icon icon="mdi-ticket-confirmation-outline" size="18" /></v-avatar>
                <div><strong>邀请与公告运营</strong><span>独立维护注册邀请码与系统公告触达。</span></div>
              </div>
              <div class="auth-security-item">
                <v-avatar size="36" rounded="lg"><v-icon icon="mdi-shield-search" size="18" /></v-avatar>
                <div><strong>安全审计可追溯</strong><span>登录与高权限操作写入安全日志。</span></div>
              </div>
            </div>
          </template>
          <template v-else>
            <span class="auth-eyebrow"><v-icon icon="mdi-shield-check-outline" size="15" /> 账号信息，清晰掌控</span>
            <h1>把重要账号<br />放在安心的位置</h1>
            <p>为不同渠道获得的账号资料，建立统一、清晰且可持续扩展的个人管理入口。</p>

            <div class="auth-security-list auth-security-list--compact">
              <div class="auth-security-item">
                <v-avatar size="36" rounded="lg"><v-icon icon="mdi-account-lock-outline" size="18" /></v-avatar>
                <div><strong>个人账号独立保存</strong><span>每位用户只管理自己的账号资料。</span></div>
              </div>
              <div class="auth-security-item">
                <v-avatar size="36" rounded="lg"><v-icon icon="mdi-layers-triple-outline" size="18" /></v-avatar>
                <div><strong>记录字段可扩展</strong><span>适配不同来源和不同类型的账号信息。</span></div>
              </div>
              <div class="auth-security-item">
                <v-avatar size="36" rounded="lg"><v-icon icon="mdi-shield-key-outline" size="18" /></v-avatar>
                <div><strong>访问边界更清晰</strong><span>个人用户与管理员使用独立入口。</span></div>
              </div>
            </div>
          </template>

          <div class="auth-aside__visual" aria-hidden="true">
            <img
              class="auth-aside__illustration"
              :src="loginVaultIllustration"
              alt=""
              width="800"
              height="600"
              decoding="async"
            />
          </div>
        </div>
      </aside>

      <section class="auth-content">
        <v-card class="auth-card" :class="{ 'auth-card--register': register }" elevation="0">
          <slot />
        </v-card>
      </section>
    </main>

    <footer class="auth-footer">
      <span>© 2026 Online Safe</span>
      <span class="d-none d-sm-inline">{{ admin ? '管理端独立入口' : '安全保存，清晰掌控' }}</span>
      <span class="d-none d-md-inline">{{ admin ? '系统运营控制台' : '个人资料管理工具' }}</span>
    </footer>
  </div>
</template>
