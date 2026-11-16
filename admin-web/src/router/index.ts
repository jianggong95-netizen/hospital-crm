import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', guest: true }
    },
    {
      path: '/',
      component: AdminLayout,
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '工作台' }
        },
        {
          path: 'roadmap',
          name: 'roadmap',
          component: () => import('@/views/RoadmapView.vue'),
          meta: { title: '交付路线' }
        },
        {
          path: 'customers',
          name: 'customers',
          component: () => import('@/views/customers/CustomersView.vue'),
          meta: { title: '客户中心', permission: 'crm:customer:read' }
        },
        {
          path: 'patient360',
          name: 'patient360',
          component: () => import('@/views/patients/Patient360View.vue'),
          meta: { title: '患者 360', permission: 'crm:patient360:read' }
        },
        {
          path: 'families',
          name: 'families',
          component: () => import('@/views/families/FamiliesView.vue'),
          meta: { title: '家庭账户', permission: 'crm:family:read' }
        },
        {
          path: 'tags',
          name: 'tags',
          component: () => import('@/views/customers/TagsView.vue'),
          meta: { title: '标签管理', permission: 'crm:tag:read' }
        },
        {
          path: 'system',
          name: 'system',
          component: () => import('@/views/SystemView.vue'),
          meta: { title: '系统信息' }
        },
        {
          path: 'system/users',
          name: 'system-users',
          component: () => import('@/views/system/UsersView.vue'),
          meta: { title: '用户管理', permission: 'system:user:read' }
        },
        {
          path: 'system/roles',
          name: 'system-roles',
          component: () => import('@/views/system/RolesView.vue'),
          meta: { title: '角色与权限', permission: 'system:role:read' }
        },
        {
          path: 'system/orgs',
          name: 'system-orgs',
          component: () => import('@/views/system/OrgsView.vue'),
          meta: { title: '组织架构', permission: 'system:org:read' }
        },
        {
          path: 'system/audit-logs',
          name: 'system-audit-logs',
          component: () => import('@/views/system/AuditLogsView.vue'),
          meta: { title: '审计日志', permission: 'system:audit:read' }
        },
        {
          path: '403',
          name: 'forbidden',
          component: () => import('@/views/ForbiddenView.vue'),
          meta: { title: '无访问权限' }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' }
    }
  ],
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.guest) {
    return auth.isAuthenticated ? { name: 'dashboard' } : true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    const restored = await auth.restore()
    if (!restored) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  const permission = to.meta.permission as string | undefined
  if (permission && !auth.has(permission)) {
    return { name: 'forbidden' }
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) || '工作台'
  document.title = `${title} · Hospital CRM`
})

export default router
