import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { navigationItems } from './navigation'

const moduleRoutes: RouteRecordRaw[] = navigationItems.filter(item => item.path !== '/dashboard').map(item => ({
  path: item.path.slice(1),
  component: () => import('@/views/ComingSoonView.vue'),
  props: { title: item.title, description: item.description },
  meta: { title: item.title },
}))

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [{
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '概览' } },
      ...moduleRoutes,
      { path: 'login', component: () => import('@/views/ComingSoonView.vue'),
        props: { title: '管理员登录', description: '账号登录启用后，可在这里完成管理员身份验证。' },
        meta: { title: '管理员登录' } },
      { path: ':pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面不存在' } },
    ],
  }],
})

router.afterEach(to => { document.title = `${String(to.meta.title ?? '管理控制台')} · 沧烁工具箱` })

export default router
