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
