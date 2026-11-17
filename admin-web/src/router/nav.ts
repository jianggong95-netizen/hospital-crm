export interface NavItem {
  path: string
  label: string
  short: string
  tint: string
  permission?: string
}

export const navItems: NavItem[] = [
  {
    path: '/',
    label: '工作台',
    short: '台',
    tint: 'linear-gradient(135deg, #2d6dad, #4c8fd8)'
  },
  {
    path: '/roadmap',
    label: '交付路线',
    short: '线',
    tint: 'linear-gradient(135deg, #2f9e73, #63bf98)'
  },
  {
    path: '/customers',
    label: '客户中心',
    short: '客',
    tint: 'linear-gradient(135deg, #2d6dad, #5a8ec5)',
    permission: 'crm:customer:read'
  },
  {
    path: '/operation',
    label: '客户运营',
    short: '营',
    tint: 'linear-gradient(135deg, #2d6dad, #63a6d3)',
    permission: 'crm:lead:read'
  },
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
    path: '/members',
    label: '会员与权益',
    short: '会',
    tint: 'linear-gradient(135deg, #b5852f, #d6ab5c)',
    permission: 'crm:member:read'
  },
  {
    path: '/followup',
    label: '随访中心',
    short: '访',
    tint: 'linear-gradient(135deg, #2f6fb5, #5c9cd6)',
    permission: 'crm:followup:read'
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
    path: '/tags',
    label: '标签管理',
    short: '签',
    tint: 'linear-gradient(135deg, #3c8f9e, #63b3bf)',
    permission: 'crm:tag:read'
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
