export interface NavItem {
  path: string
  label: string
  short: string
  tint: string
  permission?: string
}

export interface NavGroup {
  key: string
  label: string
  items: NavItem[]
}

/** 顶级入口（不属于任何分组，始终显示） */
export const navTopItems: NavItem[] = [
  {
    path: '/',
    label: '工作台',
    short: '台',
    tint: 'linear-gradient(135deg, #2d6dad, #4c8fd8)'
  }
]

/** 分组导航：组内条目按权限过滤，空组不显示 */
export const navGroups: NavGroup[] = [
  {
    key: 'customer',
    label: '客户管理',
    items: [
      {
        path: '/customers',
        label: '客户中心',
        short: '客',
        tint: 'linear-gradient(135deg, #2d6dad, #5a8ec5)',
        permission: 'crm:customer:read'
      },
      {
        path: '/patient360',
        label: '患者 360',
        short: '患',
        tint: 'linear-gradient(135deg, #175d8f, #4a9cc7)',
        permission: 'crm:patient360:read'
      },
      {
        path: '/families',
        label: '家庭账户',
        short: '家',
        tint: 'linear-gradient(135deg, #2f7f9e, #5aa8bf)',
        permission: 'crm:family:read'
      },
      {
        path: '/members',
        label: '会员与权益',
        short: '会',
        tint: 'linear-gradient(135deg, #b5852f, #d6ab5c)',
        permission: 'crm:member:read'
      }
    ]
  },
  {
    key: 'service',
    label: '就诊服务',
    items: [
      {
        path: '/appointments',
        label: '预约中心',
        short: '约',
        tint: 'linear-gradient(135deg, #3c6e9e, #6f9ec5)',
        permission: 'crm:appointment:read'
      },
      {
        path: '/escorts',
        label: '陪诊服务',
        short: '陪',
        tint: 'linear-gradient(135deg, #8a5cc6, #b08ad9)',
        permission: 'crm:escortorder:read'
      },
      {
        path: '/home-visits',
        label: '上门服务',
        short: '访',
        tint: 'linear-gradient(135deg, #c96a2b, #dd9457)',
        permission: 'crm:homevisit:read'
      },
      {
        path: '/orders',
        label: '收银与订单',
        short: '收',
        tint: 'linear-gradient(135deg, #2f8f6b, #5cba92)',
        permission: 'crm:order:read'
      },
      {
        path: '/insurance',
        label: '商保中心',
        short: '保',
        tint: 'linear-gradient(135deg, #2d6d5a, #5aa88d)',
        permission: 'crm:insurance:read'
      }
    ]
  },
  {
    key: 'marketing',
    label: '营销管理',
    items: [
      {
        path: '/operation',
        label: '客户运营',
        short: '营',
        tint: 'linear-gradient(135deg, #2d6dad, #63a6d3)',
        permission: 'crm:lead:read'
      },
      {
        path: '/tags',
        label: '标签管理',
        short: '签',
        tint: 'linear-gradient(135deg, #3c8f9e, #63b3bf)',
        permission: 'crm:tag:read'
      },
      {
        path: '/callcenter',
        label: '呼叫中心',
        short: '呼',
        tint: 'linear-gradient(135deg, #b5652f, #d69a5c)',
        permission: 'crm:call:read'
      }
    ]
  },
  {
    key: 'ops',
    label: '运营管理',
    items: [
      {
        path: '/followup',
        label: '随访中心',
        short: '访',
        tint: 'linear-gradient(135deg, #2f6fb5, #5c9cd6)',
        permission: 'crm:followup:read'
      },
      {
        path: '/complaints',
        label: '投诉表扬',
        short: '诉',
        tint: 'linear-gradient(135deg, #b5533c, #d6856f)',
        permission: 'crm:complaint:read'
      },
      {
        path: '/chat',
        label: '客服工作台',
        short: '服',
        tint: 'linear-gradient(135deg, #2f7f74, #63a89d)',
        permission: 'crm:chat:read'
      },
      {
        path: '/knowledge',
        label: 'AI 知识库',
        short: '知',
        tint: 'linear-gradient(135deg, #5b5cc6, #8a8ade)',
        permission: 'crm:knowledge:read'
      },
      {
        path: '/analytics',
        label: '运营分析',
        short: '析',
        tint: 'linear-gradient(135deg, #2d6dad, #4c8fd8)',
        permission: 'crm:analytics:read'
      }
    ]
  },
  {
    key: 'dev',
    label: '开发者管理',
    items: [
      {
        path: '/roadmap',
        label: '交付路线',
        short: '线',
        tint: 'linear-gradient(135deg, #2f9e73, #63bf98)'
      },
      {
        path: '/ops',
        label: '运维监控',
        short: '维',
        tint: 'linear-gradient(135deg, #5b6b85, #7f8fa8)',
        permission: 'crm:ops:read'
      },
      {
        path: '/system/users',
        label: '用户管理',
        short: '用',
        tint: 'linear-gradient(135deg, #2d6dad, #5a8ec5)',
        permission: 'system:user:read'
      },
      {
        path: '/system/roles',
        label: '角色与权限',
        short: '权',
        tint: 'linear-gradient(135deg, #7a5cc6, #9b84db)',
        permission: 'system:role:read'
      },
      {
        path: '/system/orgs',
        label: '组织架构',
        short: '组',
        tint: 'linear-gradient(135deg, #2f9e73, #63bf98)',
        permission: 'system:org:read'
      },
      {
        path: '/system/audit-logs',
        label: '审计日志',
        short: '审',
        tint: 'linear-gradient(135deg, #c9892b, #dcaa57)',
        permission: 'system:audit:read'
      },
      {
        path: '/system',
        label: '系统信息',
        short: '系',
        tint: 'linear-gradient(135deg, #5b6b85, #7f8fa8)'
      }
    ]
  }
]
