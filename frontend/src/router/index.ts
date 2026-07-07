import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '首页', requiresAuth: true }
  },
  {
    path: '/project',
    name: 'Project',
    component: () => import('@/views/Projects.vue'),
    meta: { title: '项目管理', requiresAuth: true }
  },
  {
    path: '/project/:id',
    name: 'ProjectDetail',
    component: () => import('@/views/ProjectDetail.vue'),
    meta: { title: '工程详情', requiresAuth: true }
  },
  {
    path: '/project/edit/:id',
    name: 'ProjectEdit',
    component: () => import('@/views/ProjectEdit.vue'),
    meta: { title: '编辑工程', requiresAuth: true }
  },
  {
    path: '/statistic',
    name: 'Statistics',
    component: () => import('@/views/Statistics.vue'),
    meta: { title: '数据统计', requiresAuth: true, roles: ['admin', 'constructor'] }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/message',
    name: 'Messages',
    component: () => import('@/views/Messages.vue'),
    meta: { title: '消息中心', requiresAuth: true }
  },
  {
    path: '/migrate',
    name: 'Migration',
    component: () => import('@/views/Migration.vue'),
    meta: { title: '数据迁移', requiresAuth: true, roles: ['admin'] }
  },
  {
    path: '/admin/space-type',
    name: 'SpaceTypeManage',
    component: () => import('@/views/admin/SpaceTypeManage.vue'),
    meta: { title: '空间类型管理', requiresAuth: true, roles: ['admin'] }
  },
  {
    path: '/admin/construction-plan',
    name: 'ConstructionPlanManage',
    component: () => import('@/views/admin/ConstructionPlanManage.vue'),
    meta: { title: '施工方案管理', requiresAuth: true, roles: ['admin'] }
  },
  {
    path: '/admin/user',
    name: 'UserManage',
    component: () => import('@/views/admin/UserManage.vue'),
    meta: { title: '用户管理', requiresAuth: true, roles: ['admin'] }
  },
  {
    path: '/admin/ai-config',
    name: 'AiConfig',
    component: () => import('@/views/admin/AiConfig.vue'),
    meta: { title: 'AI大模型配置', requiresAuth: true, roles: ['admin'] }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/Forbidden.vue'),
    meta: { title: '无权限' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false
  const requiredRoles = to.meta.roles as string[] | undefined

  if (requiresAuth && !authStore.isLoggedIn) {
    next('/login')
    NProgress.done()
    return
  }

  if (to.path === '/login' && authStore.isLoggedIn) {
    next('/dashboard')
    NProgress.done()
    return
  }

  if (requiresAuth && authStore.isLoggedIn && !authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch (error) {
      console.error('获取用户信息失败:', error)
      authStore.logout()
      next('/login')
      NProgress.done()
      return
    }
  }

  // 检查角色权限
  if (requiredRoles && authStore.userInfo) {
    const userRole = authStore.userInfo.role
    if (!requiredRoles.includes(userRole)) {
      next('/403')
      NProgress.done()
      return
    }
  }

  document.title = to.meta.title ? `${to.meta.title} - 三人行吊顶管理系统` : '三人行吊顶管理系统'

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
