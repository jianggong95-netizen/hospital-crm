export type PhaseStatus = 'active' | 'done' | 'planned'

export interface Phase {
  key: string
  name: string
  focus: string
  status: PhaseStatus
  tasks: string[]
}

export const phases: Phase[] = [
  {
    key: 'P0',
    name: '项目底座与安全',
    focus: '工程骨架、登录权限、组织、审计、配置、CI',
    status: 'done',
    tasks: [
      'CRM-001 项目初始化（已完成）',
      'CRM-002 认证、组织、角色、权限（已完成）'
    ]
  },
  {
    key: 'P1',
    name: '客户与 HIS',
    focus: '客户档案、家庭账户、HIS 适配、患者 360',
    status: 'done',
    tasks: [
      'CRM-003 客户中心（已完成）',
      'CRM-004 家庭账户（已完成）',
      'CRM-005 HIS SQL Server 适配器（已完成）',
      'CRM-006 患者 360（已完成）'
    ]
  },
  {
    key: 'P2',
    name: '服务交易',
    focus: '邀约分诊、预约、陪诊、上门服务、订单支付',
    status: 'active',
    tasks: [
      'CRM-007 客户运营、邀约与分诊（已完成）',
      'CRM-008 预约中心（已完成）',
      'CRM-009 陪诊服务（已完成）',
      'CRM-010 上门服务（已完成）',
      'CRM-011 订单、收费、支付与退款（已完成）',
      'CRM-012 会员、充值、次卡与套餐（已完成）'
    ]
  },
  {
    key: 'P3',
    name: '客户运营',
    focus: '随访、投诉表扬、消息触达',
    status: 'done',
    tasks: [
      'CRM-013 随访中心（已完成）',
      'CRM-014 投诉表扬中心（已完成）'
    ]
  },
  {
    key: 'P4',
    name: '智能客服',
    focus: '在线客服、知识库、AI 数字人、呼叫中心',
    status: 'planned',
    tasks: [
      'CRM-015 在线客服与人工工作台',
      'CRM-016 AI 知识库与数字人',
      'CRM-017 呼叫中心与短信'
    ]
  },
  {
    key: 'P5',
    name: '经营扩展',
    focus: '商保中心、运营分析',
    status: 'planned',
    tasks: [
      'CRM-018 商保中心',
      'CRM-019 运营分析'
    ]
  },
  {
    key: 'P6',
    name: '联调上线',
    focus: '系统集成、安全测试、备份恢复演练、灰度上线',
    status: 'planned',
    tasks: [
      'CRM-020 系统集成、上线与运维'
    ]
  }
]

export const phaseStatusLabels: Record<PhaseStatus, string> = {
  active: '进行中',
  done: '已完成',
  planned: '待启动'
}
