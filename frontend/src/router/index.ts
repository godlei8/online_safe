import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import VaultHomeView from '@/views/VaultHomeView.vue'
import AdminLoginView from '@/views/AdminLoginView.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import AdminDashboardView from '@/views/admin/AdminDashboardView.vue'
import AdminInvitationsView from '@/views/admin/AdminInvitationsView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminPlaceholderView from '@/views/admin/AdminPlaceholderView.vue'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/vault', name: 'vault', component: VaultHomeView },
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
            subtitle: '创建、删除和查看邀请码；邀请码仅用于注册准入，不授予管理权限。',
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
          component: AdminPlaceholderView,
          meta: { title: '系统模板', subtitle: '管理系统模板与动态字段。' },
        },
        {
          path: 'security-logs',
          name: 'admin-security-logs',
          component: AdminPlaceholderView,
          meta: { title: '安全日志', subtitle: '查看登录与管理操作日志。' },
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: AdminPlaceholderView,
          meta: { title: '系统设置', subtitle: '注册策略、公告与安全策略。' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAdmin) return true

  const adminAuth = useAdminAuthStore()
  if (!adminAuth.ready) {
    try {
      await adminAuth.bootstrap()
    } catch {
      return { path: '/admin/login', query: { redirect: to.fullPath } }
    }
  }

  if (!adminAuth.session.authenticated) {
    return { path: '/admin/login', query: { redirect: to.fullPath } }
  }

  const userAuth = useAuthStore()
  if (userAuth.session.authenticated && userAuth.session.role === 'USER') {
    // 个人会话与管理员会话互斥：进入管理页时以管理员会话为准（后登录覆盖）
  }

  return true
})

export default router
