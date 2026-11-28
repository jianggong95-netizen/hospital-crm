// ------------------------------------------------------------------
// 分类 / 模板 / 计划
// ------------------------------------------------------------------
export type FollowupConfigStatus = 'active' | 'disabled'

export type FollowupChannel = 'phone' | 'sms' | 'mini_program' | 'h5' | 'ai' | 'manual'

export type FollowupTriggerType =
  | 'APPOINTMENT_COMPLETED'
  | 'SERVICE_COMPLETED'
  | 'ORDER_PAID'
  | 'MANUAL'
  | 'HIS_OUTPATIENT_VISIT'
  | 'HIS_DISCHARGE'
  | 'HIS_SURGERY'
  | 'HIS_EXAM_REPORT'

export type FollowupVisitType = 'OUTPATIENT' | 'INPATIENT' | 'SURGERY' | 'EXAM' | 'MARKETING'

export interface FollowupCategory {
  categoryCode: string
  categoryName: string
  visitType: FollowupVisitType
  description?: string
  status: FollowupConfigStatus
  sortNo: number
}

export interface FollowupTemplate {
  templateCode: string
  categoryCode: string
  templateName: string
  channel: FollowupChannel
  questions: Array<Record<string, unknown>>
  riskRules: Record<string, unknown>
  version: number
  status: FollowupConfigStatus
  description?: string
}

export interface FollowupPlan {
  planNo: string
  planName: string
  categoryCode: string
  categoryName?: string
  templateCode: string
  triggerType: FollowupTriggerType
  offsetDays: number
  channel: FollowupChannel
  defaultAssigneeId?: string
  defaultAssigneeName?: string
  status: FollowupConfigStatus
  description?: string
}

// ------------------------------------------------------------------
// 任务
// ------------------------------------------------------------------
export type FollowupTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'ESCALATED' | 'CANCELLED'

export type FollowupRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface FollowupTask {
  id: string
  taskNo: string
  customerId: string
  customerName?: string
  phoneMasked?: string
  categoryCode: string
  categoryName?: string
  templateCode?: string
  channel: string
  assigneeId?: string
  assigneeName?: string
  plannedAt: string
  status: FollowupTaskStatus
  riskLevel?: FollowupRiskLevel
  escalationRequired: boolean
  resultCode?: string
  completedAt?: string
  createdAt: string
  planNo?: string
}

export interface FollowupRecord {
  id: string
  riskLevel: FollowupRiskLevel
  answers: Record<string, string>
  resultCode?: string
  remark?: string
  operatorId?: string
  createdAt: string
}

export interface FollowupTaskDetail {
  task: FollowupTask
  questions: Array<Record<string, unknown>>
  records: FollowupRecord[]
}

export interface FollowupTaskCreatePayload {
  customerId: string
  categoryCode: string
  templateCode?: string
  channel: FollowupChannel
  plannedAt: string
  assigneeId?: string
}

export interface FollowupTriggerPayload {
  triggerType: FollowupTriggerType
  eventKey: string
  customerId: string
  occurredAt?: string
}

export interface FollowupTriggerResult {
  created: number
  skipped: number
  tasks: FollowupTask[]
}

export interface FollowupVisitTypeStat {
  visitType: FollowupVisitType
  visitTypeLabel: string
  categoryCode?: string | null
  categoryName?: string | null
  totalTasks: number
  completedTasks: number
  pendingTasks: number
  inProgressTasks: number
  escalatedTasks: number
  cancelledTasks: number
  overdueTasks: number
  completionRate: number
}

export interface FollowupStats {
  total: number
  pending: number
  inProgress: number
  completed: number
  escalated: number
  cancelled: number
  overdue: number
  completionRate: number
  visitTypes: FollowupVisitTypeStat[]
}

// ------------------------------------------------------------------
// HIS 事件对接
// ------------------------------------------------------------------
export type FollowupHisEventType =
  | 'HIS_OUTPATIENT_VISIT'
  | 'HIS_DISCHARGE'
  | 'HIS_SURGERY'
  | 'HIS_EXAM_REPORT'

export interface FollowupHisEventPayload {
  eventType: FollowupHisEventType
  eventKey: string
  customerId?: string
  externalPatientId?: string
  sourceSystem?: string
  occurredAt?: string
  payload?: Record<string, unknown>
}

export interface FollowupHisEvent {
  id: string
  eventKey: string
  eventType: FollowupHisEventType
  customerId?: string
  customerName?: string
  externalPatientId?: string
  sourceSystem: string
  occurredAt?: string
  status: 'PROCESSED' | 'NO_MATCH' | 'FAILED'
  createdTasks: number
  skippedTasks: number
  errorMessage?: string
  createdAt: string
}
