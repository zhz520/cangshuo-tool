import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { navigationItems } from './navigation'
import { useAuthStore } from '@/stores/auth'

const moduleRoutes: RouteRecordRaw[] = navigationItems
  .filter(item => !['/dashboard', '/tools', '/categories', '/banners', '/users', '/announcements', '/feedback', '/logs'].includes(item.path)).map(item => ({
  path: item.path.slice(1),
  component: () => import('@/views/ComingSoonView.vue'),
  props: { title: item.title, description: item.description },
  meta: { title: item.title },
}))

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [{
    path: '/login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '管理员登录', public: true },
  }, {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '概览' } },
      { path: 'tools', component: () => import('@/views/ToolsView.vue'), meta: { title: '工具管理' } },
      { path: 'categories', component: () => import('@/views/CategoriesView.vue'), meta: { title: '分类管理' } },
      { path: 'banners', component: () => import('@/views/RecommendationsView.vue'), meta: { title: '推荐位管理' } },
      { path: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理' } },
      { path: 'announcements', component: () => import('@/views/AnnouncementsView.vue'), meta: { title: '公告管理' } },
      { path: 'feedback', component: () => import('@/views/FeedbackView.vue'), meta: { title: '用户反馈' } },
      { path: 'logs', component: () => import('@/views/OperationLogsView.vue'), meta: { title: '操作日志' } },
      ...moduleRoutes,
      { path: ':pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面不存在' } },
    ],
  }],
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  const isPublic = to.meta.public === true
  if (!auth.isAuthenticated) {
    return isPublic ? true : { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (!auth.profile) {
    try {
      await auth.ensureProfile()
    } catch {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (!auth.isAuthenticated) return { path: '/login' }
  return isPublic ? { path: '/dashboard' } : true
})

router.afterEach(to => { document.title = `${String(to.meta.title ?? '管理控制台')} · 沧烁工具箱` })

export default router
