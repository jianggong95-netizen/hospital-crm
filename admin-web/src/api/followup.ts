import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  FollowupCategory,
  FollowupHisEvent,
  FollowupHisEventPayload,
  FollowupPlan,
  FollowupStats,
  FollowupTask,
  FollowupTaskCreatePayload,
  FollowupTaskDetail,
  FollowupTaskStatus,
  FollowupTemplate,
  FollowupTriggerPayload,
  FollowupTriggerResult
} from '@/types/followup'

export interface FollowupTaskQuery {
  keyword?: string
  status?: FollowupTaskStatus | ''
  categoryCode?: string
  riskLevel?: string
  assigneeId?: string
  customerId?: string
  escalationRequired?: boolean
  overdue?: boolean
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

// ------------------------------------------------------------------
// 分类
// ------------------------------------------------------------------
export function fetchFollowupCategories(status?: string, visitType?: string): Promise<FollowupCategory[]> {
  return api.get<FollowupCategory[]>('/followup/categories', { status, visitType })
}

export function createFollowupCategory(payload: Partial<FollowupCategory>): Promise<FollowupCategory> {
  return api.post<FollowupCategory>('/followup/categories', payload)
}

export function updateFollowupCategory(
  categoryCode: string,
  payload: Partial<FollowupCategory>
): Promise<FollowupCategory> {
  return api.put<FollowupCategory>(`/followup/categories/${categoryCode}`, payload)
}

// ------------------------------------------------------------------
// 模板
// ------------------------------------------------------------------
export function fetchFollowupTemplates(params: {
  categoryCode?: string
  status?: string
}): Promise<FollowupTemplate[]> {
  return api.get<FollowupTemplate[]>('/followup/templates', params)
}

export function createFollowupTemplate(payload: Partial<FollowupTemplate>): Promise<FollowupTemplate> {
  return api.post<FollowupTemplate>('/followup/templates', payload)
}

export function updateFollowupTemplate(
  templateCode: string,
  payload: Partial<FollowupTemplate>
): Promise<FollowupTemplate> {
  return api.put<FollowupTemplate>(`/followup/templates/${templateCode}`, payload)
}

export function changeFollowupTemplateStatus(
  templateCode: string,
  status: string
): Promise<FollowupTemplate> {
  return api.put<FollowupTemplate>(`/followup/templates/${templateCode}/status`, { status })
}

// ------------------------------------------------------------------
// 计划
// ------------------------------------------------------------------
export function fetchFollowupPlans(status?: string): Promise<FollowupPlan[]> {
  return api.get<FollowupPlan[]>('/followup/plans', { status })
}

export function createFollowupPlan(payload: Partial<FollowupPlan>): Promise<FollowupPlan> {
  return api.post<FollowupPlan>('/followup/plans', payload)
}

export function updateFollowupPlan(planNo: string, payload: Partial<FollowupPlan>): Promise<FollowupPlan> {
  return api.put<FollowupPlan>(`/followup/plans/${planNo}`, payload)
}

export function changeFollowupPlanStatus(planNo: string, status: string): Promise<FollowupPlan> {
  return api.put<FollowupPlan>(`/followup/plans/${planNo}/status`, { status })
}

// ------------------------------------------------------------------
// 任务
// ------------------------------------------------------------------
export function fetchFollowupTasks(query: FollowupTaskQuery): Promise<PageResponse<FollowupTask>> {
  return api.get<PageResponse<FollowupTask>>('/followup/tasks', query)
}

export interface FollowupStatsQuery {
  visitType?: string
  groupBy?: 'visitType' | 'category'
}

export function fetchFollowupStats(query?: FollowupStatsQuery): Promise<FollowupStats> {
  return api.get<FollowupStats>('/followup/stats', query)
}

export function fetchFollowupTaskDetail(taskId: string): Promise<FollowupTaskDetail> {
  return api.get<FollowupTaskDetail>(`/followup/tasks/${taskId}`)
}

export function createFollowupTask(payload: FollowupTaskCreatePayload): Promise<FollowupTask> {
  return api.post<FollowupTask>('/followup/tasks', payload)
}

export function triggerFollowup(payload: FollowupTriggerPayload): Promise<FollowupTriggerResult> {
  return api.post<FollowupTriggerResult>('/followup/triggers', payload)
}

export function assignFollowupTask(taskId: string, assigneeId: string): Promise<FollowupTask> {
  return api.put<FollowupTask>(`/followup/tasks/${taskId}/assign`, { assigneeId })
}

export function startFollowupTask(taskId: string): Promise<FollowupTask> {
  return api.put<FollowupTask>(`/followup/tasks/${taskId}/start`)
}

export function submitFollowupRecord(
  taskId: string,
  payload: { answers: Record<string, string>; resultCode?: string; remark?: string }
): Promise<FollowupTaskDetail> {
  return api.post<FollowupTaskDetail>(`/followup/tasks/${taskId}/records`, payload)
}

export function escalateFollowupTask(taskId: string, reason: string): Promise<FollowupTask> {
  return api.put<FollowupTask>(`/followup/tasks/${taskId}/escalate`, { reason })
}

export function resolveFollowupTask(taskId: string, remark?: string): Promise<FollowupTask> {
  return api.put<FollowupTask>(`/followup/tasks/${taskId}/resolve`, { remark })
}

export function cancelFollowupTask(taskId: string, reason: string): Promise<FollowupTask> {
  return api.put<FollowupTask>(`/followup/tasks/${taskId}/cancel`, { reason })
}

// ------------------------------------------------------------------
// HIS 事件对接
// ------------------------------------------------------------------
export function pushFollowupHisEvent(payload: FollowupHisEventPayload): Promise<FollowupHisEvent> {
  return api.post<FollowupHisEvent>('/followup/his/events', payload)
}

export function fetchFollowupHisEvents(params: {
  status?: string
  page?: number
  pageSize?: number
}): Promise<PageResponse<FollowupHisEvent>> {
  return api.get<PageResponse<FollowupHisEvent>>('/followup/his/events', params)
}
