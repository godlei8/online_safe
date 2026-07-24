import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import VaultLayout from '@/layouts/VaultLayout.vue'
import VaultHomeView from '@/views/VaultHomeView.vue'
import VaultItemDetailView from '@/views/vault/VaultItemDetailView.vue'
import VaultTemplatesView from '@/views/vault/VaultTemplatesView.vue'
import VaultProfileView from '@/views/vault/VaultProfileView.vue'
import VaultSecurityView from '@/views/vault/VaultSecurityView.vue'
import AdminLoginView from '@/views/AdminLoginView.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import AdminDashboardView from '@/views/admin/AdminDashboardView.vue'
import AdminInvitationsView from '@/views/admin/AdminInvitationsView.vue'
import AdminAnnouncementsView from '@/views/admin/AdminAnnouncementsView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminTemplatesView from '@/views/admin/AdminTemplatesView.vue'
import AdminSecurityLogsView from '@/views/admin/AdminSecurityLogsView.vue'
import AdminSettingsView from '@/views/admin/AdminSettingsView.vue'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useAuthStore } from '@/stores/auth'
import { useVaultStore } from '@/stores/vault'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView },
    { path: '/vault/setup', redirect: '/vault' },
    { path: '/vault/unlock', redirect: '/vault' },
    { path: '/vault/rewrap', redirect: '/vault' },
    {
      path: '/vault',
      component: VaultLayout,
      meta: { requiresUser: true, requiresVaultReady: true },
      children: [
        { path: '', name: 'vault', component: VaultHomeView },
        { path: 'templates', name: 'vault-templates', component: VaultTemplatesView },
        { path: 'profile', name: 'vault-profile', component: VaultProfileView },
        { path: 'security', name: 'vault-security', component: VaultSecurityView },
        { path: 'items/:id', name: 'vault-item', component: VaultItemDetailView },
      ],
    },
    {
      path: '/vault/new',
      redirect: (to) => ({
        path: '/vault',
        query: {
          new: '1',
          ...(typeof to.query.templateId === 'string' ? { templateId: to.query.templateId } : {}),
        },
      }),
    },
    {
      path: '/vault/items/:id/edit',
      redirect: (to) => ({
        path: `/vault/items/${String(to.params.id)}`,
        query: { edit: String(to.params.id) },
      }),
    },
    { path: '/admin/login', name: 'admin-login', component: AdminLoginView },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: AdminDashboardView,
          meta: {
            title: '系统概览',
            subtitle: '查看服务运行状态、用户概况和需要处理的安全事项。',
          },
        },
        {
          path: 'invitations',
          name: 'admin-invitations',
          component: AdminInvitationsView,
          meta: {
            title: '邀请码管理',
            subtitle: '创建、删除和查看邀请码；当前注册不强制邀请码，后台功能保留。',
          },
        },
        {
          path: 'announcements',
          name: 'admin-announcements',
          component: AdminAnnouncementsView,
          meta: {
            title: '公告管理',
            subtitle: '发布系统公告；用户端铃铛可查看，未读最新一条会强制确认。',
          },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: AdminUsersView,
          meta: {
            title: '用户管理',
            subtitle: '可按手机号、用户名、状态和注册时间查询；启用/禁用用户并可使会话失效。管理员仅可见元数据。',
          },
        },
        {
          path: 'templates',
          name: 'admin-templates',
          component: AdminTemplatesView,
          meta: {
            title: '系统模板',
            subtitle: '维护全体用户可用的字段结构模板；发布后可在保险箱选用创建。',
          },
        },
        {
          path: 'security-logs',
          name: 'admin-security-logs',
          component: AdminSecurityLogsView,
          meta: { title: '安全日志', subtitle: '查看登录与管理操作日志。' },
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: AdminSettingsView,
          meta: { title: '系统设置', subtitle: '注册策略、个人登录会话上限、邀请码默认值与安全日志保留期限。' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.ready) {
    await auth.bootstrap()
  }

  if (to.meta.requiresAdmin) {
    const adminAuth = useAdminAuthStore()
    if (!adminAuth.ready) {
      await adminAuth.bootstrap()
    }
    if (!adminAuth.session.authenticated) {
      return { path: '/admin/login', query: { redirect: to.fullPath } }
    }
    return true
  }

  if (to.meta.requiresUser || to.path.startsWith('/vault')) {
    if (!auth.session.authenticated) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    const vault = useVaultStore()
    if (!vault.ready) {
      await vault.refreshInitialization()
    }
  }

  return true
})

export default router
