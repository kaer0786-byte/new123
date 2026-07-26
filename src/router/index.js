import { createRouter, createWebHashHistory } from 'vue-router'
import { auth } from '@/store/auth'

const routes = [
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '数据看板' } },
      { path: 'products', component: () => import('@/views/ProductsView.vue'), meta: { title: '商品库' } },
      { path: 'mappings', component: () => import('@/views/MappingsView.vue'), meta: { title: '映射管理' } },
      { path: 'tasks', component: () => import('@/views/TasksView.vue'), meta: { title: '任务日志' } },
      { path: 'tasks/:id', component: () => import('@/views/TaskDetailView.vue'), meta: { title: '任务详情' } },
      { path: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '配置中心', adminOnly: true } },
      { path: 'members', component: () => import('@/views/MembersView.vue'), meta: { title: '成员管理', adminOnly: true } }
    ]
  }
]

export const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(to => {
  if (to.meta.public) return true
  if (!auth.loggedIn) return '/login'
  if (to.meta.adminOnly && !auth.isAdmin) return '/dashboard'
  return true
})
