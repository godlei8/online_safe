import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import VaultHomeView from '@/views/VaultHomeView.vue'
import AdminLoginView from '@/views/AdminLoginView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/vault', name: 'vault', component: VaultHomeView },
    { path: '/admin/login', name: 'admin-login', component: AdminLoginView },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})
