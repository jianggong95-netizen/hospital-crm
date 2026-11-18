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
          path: 'operation',
          name: 'operation',
          component: () => import('@/views/operation/OperationView.vue'),
          meta: { title: '客户运营', permission: 'crm:lead:read' }
        },
        {
          path: 'appointments',
          name: 'appointments',
          component: () => import('@/views/appointment/AppointmentView.vue'),
          meta: { title: '预约中心', permission: 'crm:appointment:read' }
        },
        {
          path: 'escorts',
          name: 'escorts',
          component: () => import('@/views/escort/EscortView.vue'),
          meta: { title: '陪诊服务', permission: 'crm:escortorder:read' }
        },
        {
          path: 'home-visits',
          name: 'home-visits',
          component: () => import('@/views/homevisit/HomeVisitView.vue'),
          meta: { title: '上门服务', permission: 'crm:homevisit:read' }
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/order/OrderView.vue'),
          meta: { title: '收银与订单', permission: 'crm:order:read' }
        },
        {
          path: 'members',
          name: 'members',
          component: () => import('@/views/member/MemberView.vue'),
          meta: { title: '会员与权益', permission: 'crm:member:read' }
        },
        {
          path: 'followup',
          name: 'followup',
          component: () => import('@/views/followup/FollowupView.vue'),
          meta: { title: '随访中心', permission: 'crm:followup:read' }
        },
        {
          path: 'complaints',
          name: 'complaints',
          component: () => import('@/views/complaint/ComplaintView.vue'),
          meta: { title: '投诉表扬', permission: 'crm:complaint:read' }
        },
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/chat/ChatWorkbenchView.vue'),
          meta: { title: '客服工作台', permission: 'crm:chat:read' }
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/knowledge/KnowledgeView.vue'),
          meta: { title: 'AI 知识库', permission: 'crm:knowledge:read' }
        },
        {
          path: 'callcenter',
          name: 'callcenter',
          component: () => import('@/views/callcenter/CallCenterView.vue'),
          meta: { title: '呼叫中心', permission: 'crm:call:read' }
        },
        {
          path: 'insurance',
          name: 'insurance',
          component: () => import('@/views/insurance/InsuranceView.vue'),
          meta: { title: '商保中心', permission: 'crm:insurance:read' }
        },
        {
          path: 'analytics',
          name: 'analytics',
          component: () => import('@/views/analytics/AnalyticsView.vue'),
          meta: { title: '运营分析', permission: 'crm:analytics:read' }
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
